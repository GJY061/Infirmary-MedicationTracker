package com.example.infirmary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.infirmary.data.MedTrackRepository

/** Manual dependency injection: supplies the shared repository when Android creates a ViewModel. */
class AppViewModelFactory(
    private val repository: MedTrackRepository
) : ViewModelProvider.Factory {
    /**
     * Matches the requested ViewModel class to its repository-taking constructor.
     * Android requests T; each checked branch constructs that matching concrete type.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            PatientViewModel::class.java -> PatientViewModel(repository) as T
            MedicationViewModel::class.java -> MedicationViewModel(repository) as T
            SymptomViewModel::class.java -> SymptomViewModel(repository) as T
            MedCoachViewModel::class.java -> MedCoachViewModel(repository) as T
            ClinicianViewModel::class.java -> ClinicianViewModel(repository) as T
            SymptomTrendViewModel::class.java -> SymptomTrendViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
        }
    }
}
