
package com.example.infirmary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.infirmary.data.MedTrackRepository
import com.example.infirmary.data.SymptomEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns the patient's reactive symptom history and save-form status. */
class SymptomViewModel(
    private val repository: MedTrackRepository
) : ViewModel() {
    // Capture the session for this instance; navigation uses a patient-specific ViewModel key.
    val patientId: String? = repository.getLoggedInPatientId()

    // stateIn shares the Room Flow and caches its latest value for UI collectors.
    // WhileSubscribed keeps the upstream active for 5 seconds after the last collector leaves.
    val symptoms: StateFlow<List<SymptomEntity>> = patientId?.let { id ->
        repository.observeSymptoms(id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    } ?: flowOf(emptyList<SymptomEntity>())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _formState = MutableStateFlow(FormUiState())
    val formState: StateFlow<FormUiState> = _formState.asStateFlow()

    /** Validates category/severity, captures the local timestamp, and asynchronously saves the record. */
    fun saveSymptom(category: String, severity: Int, notes: String) {
        val activePatientId = patientId
        val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        // Reject invalid input before starting a database write.
        val validationError = when {
            activePatientId.isNullOrBlank() -> "No logged-in patient found"
            category.trim().isBlank() -> "Select a category"
            severity !in 1..10 -> "Severity must be 1 to 10"
            else -> null
        }
        if (validationError != null) {
            _formState.value = FormUiState(errorMessage = validationError)
            return
        }
        val savedPatientId = activePatientId.orEmpty()
        viewModelScope.launch {
            _formState.value = FormUiState(isSaving = true)
            repository.addSymptom(savedPatientId, category, severity, notes, dateTime)
            _formState.value = FormUiState(infoMessage = "Symptom saved")
        }
    }
}
