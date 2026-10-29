package com.example.infirmary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.infirmary.data.MedTrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Transforms observed symptom records into chart points and a category-frequency summary. */
class SymptomTrendViewModel(
    repository: MedTrackRepository
) : ViewModel() {
    private val patientId: String? = repository.getLoggedInPatientId()

    // stateIn shares the Room Flow and caches its latest value for UI collectors.
    // WhileSubscribed keeps the upstream active for 5 seconds after the last collector leaves.
    val state: StateFlow<SymptomTrendUiState> = patientId?.let { id ->
        repository.observeSymptomsForTrend(id)
            .map { symptoms ->
                SymptomTrendUiState(
                    points = symptoms.map {
                        SymptomTrendPoint(
                            label = it.dateTime,
                            category = it.category,
                            severity = it.severity
                        )
                    },
                    // Count each category, then sort by descending frequency with alphabetical tie-breaking.
                    categoryCounts = symptoms
                        .groupingBy { it.category }
                        .eachCount()
                        .toList()
                        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SymptomTrendUiState())
    } ?: flowOf(SymptomTrendUiState(errorMessage = "No logged-in patient found."))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SymptomTrendUiState())
}

/** Chart points and sorted category counts derived from the same symptom emission. */
data class SymptomTrendUiState(
    val points: List<SymptomTrendPoint> = emptyList(),
    val categoryCounts: List<Pair<String, Int>> = emptyList(),
    val errorMessage: String = ""
)

/** Display-only chart record containing a timestamp label, category, and severity. */
data class SymptomTrendPoint(
    val label: String,
    val category: String,
    val severity: Int
)
