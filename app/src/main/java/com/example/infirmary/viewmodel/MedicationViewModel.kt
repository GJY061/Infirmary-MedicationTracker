package com.example.infirmary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.infirmary.data.MedTrackRepository
import com.example.infirmary.data.MedicationEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns medication observations, today's taken statuses, and add-medication form state. */
class MedicationViewModel(
    private val repository: MedTrackRepository
) : ViewModel() {
    // Capture the session for this instance; navigation uses a patient-specific ViewModel key.
    val patientId: String? = repository.getLoggedInPatientId()

    // stateIn shares the Room Flow and caches its latest value for UI collectors.
    // WhileSubscribed keeps the upstream active for 5 seconds after the last collector leaves.
    val medications: StateFlow<List<MedicationEntity>> = patientId?.let { id ->
        repository.observeMedications(id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    } ?: flowOf(emptyList<MedicationEntity>())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Convert status rows to an ID -> Boolean map for quick checkbox lookup on Home.
    val takenStatuses: StateFlow<Map<Long, Boolean>> = patientId?.let { id ->
        repository.observeTodayTakenStatuses(id)
            .map { statuses -> statuses.associate { it.medicationId to it.taken } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    } ?: flowOf(emptyMap<Long, Boolean>())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _formState = MutableStateFlow(FormUiState())
    val formState: StateFlow<FormUiState> = _formState.asStateFlow()

    /** Validates the form, saves through the repository, then invokes onSaved for navigation. */
    fun saveMedication(
        name: String,
        dosage: String,
        frequency: String,
        scheduledTime: String,
        type: String,
        notes: String,
        onSaved: () -> Unit
    ) {
        val activePatientId = patientId
        val validationError = when {
            activePatientId.isNullOrBlank() -> "No logged-in patient found"
            name.trim().isBlank() -> "Medication name is required"
            validateDosage(dosage).isNotBlank() -> validateDosage(dosage)
            frequency.trim().isBlank() -> "Select a frequency"
            type.trim().isBlank() -> "Select a medication type"
            else -> null
        }
        if (validationError != null) {
            _formState.value = FormUiState(errorMessage = validationError)
            return
        }
        val savedPatientId = activePatientId.orEmpty()
        // Start work in the ViewModel lifetime; repository suspension keeps the UI responsive.
        viewModelScope.launch {
            _formState.value = FormUiState(isSaving = true)
            repository.addMedication(
                savedPatientId,
                name,
                dosage,
                frequency,
                scheduledTime,
                type,
                notes
            )
            _formState.value = FormUiState(infoMessage = "Medication saved")
            // onSaved is a function that is passed that calls once medication is saved.
            // Return to Home only after the insert finishes; the medication Flow refreshes its list.
            onSaved()
        }
    }

    /** Launches the checkbox write; the observed Room Flow supplies the updated UI value. */
    fun setMedicationTaken(medicationId: Long, taken: Boolean) {
        val activePatientId = patientId ?: return
        viewModelScope.launch {
            repository.setMedicationTaken(activePatientId, medicationId, taken)
        }
    }
}

/** Accepts a number followed immediately by mg, ml, or g, such as 250mg or 2.5ml. */
private fun validateDosage(value: String): String {
    if (value.isBlank()) return "Dosage is required"
    return if (Regex("^\\d+(\\.\\d+)?(mg|ml|g)$", RegexOption.IGNORE_CASE).matches(value.trim())) {
        ""
    } else {
        "Use a number followed by mg, ml, or g"
    }
}
