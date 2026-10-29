package com.example.infirmary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.infirmary.data.DrugInfo
import com.example.infirmary.data.DrugLookupResult
import com.example.infirmary.data.MedCoachTipEntity
import com.example.infirmary.data.MedTrackRepository
import com.example.infirmary.data.TipGenerationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Coordinates drug lookup, personalised-tip generation, and saved-tip history for MedCoach. */
class MedCoachViewModel(
    private val repository: MedTrackRepository
) : ViewModel() {
    // Capture the session for this instance; navigation uses a patient-specific ViewModel key.
    val patientId: String? = repository.getLoggedInPatientId()

    // stateIn shares the Room Flow and caches its latest value for UI collectors.
    // WhileSubscribed keeps the upstream active for 5 seconds after the last collector leaves.
    val medicationNames: StateFlow<List<String>> = patientId?.let { id ->
        repository.observeMedications(id)
            .map { medications -> medications.map { it.name.trim() }.filter { it.isNotBlank() }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    } ?: flowOf(emptyList<String>())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(MedCoachUiState())
    val state: StateFlow<MedCoachUiState> = _state.asStateFlow()

    // This collector lasts for the ViewModel lifetime, keeping history current after new tips are saved.
    init {
        val activePatientId = patientId
        if (!activePatientId.isNullOrBlank()) {
            viewModelScope.launch {
                repository.observeMedCoachTips(activePatientId).collect { tips ->
                    _state.value = _state.value.copy(tipHistory = tips)
                }
            }
        }
    }

    /** Stores typed search text without starting an API request. */
    fun updateQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    /** Copies a dropdown medication name into the same search query used by manual typing. */
    fun selectMedication(name: String) {
        _state.value = _state.value.copy(query = name)
    }

    /** Captures the query, shows lookup loading state, and maps the repository result to the UI. */
    fun lookupDrug() {
        val query = _state.value.query
        viewModelScope.launch {
            _state.value = _state.value.copy(isLookupLoading = true, lookupErrorMessage = "")
            when (val result = repository.lookupDrugInfo(query)) {
                is DrugLookupResult.Success -> {
                    _state.value = _state.value.copy(
                        isLookupLoading = false,
                        lookupErrorMessage = "",
                        info = result.info
                    )
                }

                is DrugLookupResult.Error -> {
                    _state.value = _state.value.copy(
                        isLookupLoading = false,
                        lookupErrorMessage = result.message,
                        info = null
                    )
                }
            }
        }
    }

    /** Requires a session, shows generation progress, and exposes either the latest tip or an error. */
    fun generateTip() {
        val activePatientId = patientId
        if (activePatientId.isNullOrBlank()) {
            _state.value = _state.value.copy(tipErrorMessage = "No logged-in patient found.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isTipLoading = true, tipErrorMessage = "")
            // Sealed result types make success and expected failure explicit in the UI state.
            when (val result = repository.generatePersonalizedTip(activePatientId)) {
                is TipGenerationResult.Success -> {
                    _state.value = _state.value.copy(
                        isTipLoading = false,
                        tipErrorMessage = "",
                        latestTip = result.tipText
                    )
                }

                is TipGenerationResult.Error -> {
                    _state.value = _state.value.copy(
                        isTipLoading = false,
                        tipErrorMessage = result.message
                    )
                }
            }
        }
    }

    /** Opens the history dialog; its rows come from the existing Room observation. */
    fun showTipHistory() {
        _state.value = _state.value.copy(isTipHistoryVisible = true)
    }

    /** Closes the history dialog without deleting any saved tips. */
    fun hideTipHistory() {
        _state.value = _state.value.copy(isTipHistoryVisible = false)
    }
}

/** Independent lookup/tip loading and error fields let each MedCoach operation update separately. */
data class MedCoachUiState(
    val query: String = "",
    val isLookupLoading: Boolean = false,
    val lookupErrorMessage: String = "",
    val info: DrugInfo? = null,
    val isTipLoading: Boolean = false,
    val tipErrorMessage: String = "",
    val latestTip: String = "",
    val tipHistory: List<MedCoachTipEntity> = emptyList(),
    val isTipHistoryVisible: Boolean = false
)
