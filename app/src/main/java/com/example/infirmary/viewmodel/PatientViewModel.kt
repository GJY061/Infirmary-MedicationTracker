package com.example.infirmary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.infirmary.data.AuthResult
import com.example.infirmary.data.MedTrackRepository
import com.example.infirmary.data.PatientEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns startup readiness, the current local session, and authentication UI state. */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientViewModel(
    private val repository: MedTrackRepository
) : ViewModel() {
    // Only this ViewModel mutates the backing flows; screens receive read-only StateFlow views.
    private val _sessionState = MutableStateFlow(PatientSessionUiState())
    val sessionState: StateFlow<PatientSessionUiState> = _sessionState.asStateFlow()

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    // flatMapLatest cancels observation of the old patient when the session ID changes.
    val patient: StateFlow<PatientEntity?> = sessionState
        .flatMapLatest { state ->
            state.patientId?.let(repository::observePatient) ?: flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Initialise data before marking the session ready, so navigation cannot race CSV seeding.
    init {
        viewModelScope.launch {
            repository.seedIfNeeded()
            _sessionState.value = PatientSessionUiState(
                isReady = true,
                patientId = repository.getLoggedInPatientId()
            )
        }
    }

    /** Validates required credentials, starts a coroutine, and forwards the repository outcome. */
    fun login(patientId: String, password: String, onSuccess: (String) -> Unit) {
        val validationError = when {
            patientId.trim().isBlank() -> "PatientID is required"
            password.trim().isBlank() -> "Password is required"
            else -> null
        }
        if (validationError != null) {
            _authState.value = AuthUiState(errorMessage = validationError)
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            handleAuthResult(repository.login(patientId, password), onSuccess)
        }
    }

    /** Validates the claim form before matching the seeded account in the repository. */
    fun claim(patientId: String, phoneNumber: String, password: String, onSuccess: (String) -> Unit) {
        val validationError = when {
            patientId.trim().isBlank() -> "PatientID is required"
            phoneNumber.trim().isBlank() -> "Phone number is required"
            validatePassword(password).isNotBlank() -> validatePassword(password)
            else -> null
        }
        if (validationError != null) {
            _authState.value = AuthUiState(errorMessage = validationError)
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            handleAuthResult(repository.claimAccount(patientId, phoneNumber, password), onSuccess)
        }
    }

    /** Validates registration, creates the local account, and calls onSuccess after session update. */
    fun signUp(
        name: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String,
        onSuccess: (String) -> Unit
    ) {
        val validationError = when {
            name.trim().isBlank() -> "Full name is required"
            validatePhone(phoneNumber).isNotBlank() -> validatePhone(phoneNumber)
            validatePassword(password).isNotBlank() -> validatePassword(password)
            confirmPassword != password -> "Passwords do not match"
            else -> null
        }
        if (validationError != null) {
            _authState.value = AuthUiState(errorMessage = validationError)
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState(isLoading = true)
            when (val result = repository.signUp(name, phoneNumber, password)) {
                is AuthResult.Success -> {
                    _sessionState.value = _sessionState.value.copy(patientId = result.patientId)
                    _authState.value = AuthUiState(
                        infoMessage = "Account created. Your PatientID is ${result.patientId}"
                    )
                    onSuccess(result.patientId)
                }

                is AuthResult.Error -> _authState.value = AuthUiState(errorMessage = result.message)
            }
        }
    }

    /** Clears the saved and in-memory session; the screen callback handles navigation separately. */
    fun logout() {
        repository.clearLoggedInPatientId()
        _sessionState.value = _sessionState.value.copy(patientId = null)
        _authState.value = AuthUiState()
    }

    /** Updates session/UI state and invokes navigation only for successful authentication. */
    private fun handleAuthResult(result: AuthResult, onSuccess: (String) -> Unit) {
        when (result) {
            is AuthResult.Success -> {
                _sessionState.value = _sessionState.value.copy(patientId = result.patientId)
                _authState.value = AuthUiState()
                onSuccess(result.patientId)
            }

            is AuthResult.Error -> _authState.value = AuthUiState(errorMessage = result.message)
        }
    }
}

/** Startup/session state: isReady prevents navigation before seeding and session lookup finish. */
data class PatientSessionUiState(
    val isReady: Boolean = false,
    val patientId: String? = null
)

/** Immutable authentication display state for progress, validation failures, and success messages. */
data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String = "",
    val infoMessage: String = ""
)

/** Returns an empty string for a ten-digit 04-prefixed number, or a validation message. */
private fun validatePhone(phone: String): String {
    if (phone.isBlank()) return "Phone number is required"
    return if (Regex("^04\\d{8}$").matches(phone.trim())) "" else "Phone number must start with 04 and be 10 digits"
}

/** Requires at least eight characters with a letter and digit; returns an error or empty text. */
private fun validatePassword(password: String): String {
    if (password.isBlank()) return "Password is required"
    return if (Regex("^(?=.*[A-Za-z])(?=.*\\d).{8,}$").matches(password.trim())) "" else "Password must be 8+ chars with letters and numbers"
}
