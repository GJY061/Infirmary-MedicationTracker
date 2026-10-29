package com.example.infirmary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.infirmary.data.ClinicianInsightsResult
import com.example.infirmary.data.ClinicianStats
import com.example.infirmary.data.MedTrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns the local clinician-access check, aggregate snapshot, and generated dashboard insights. */
class ClinicianViewModel(
    private val repository: MedTrackRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ClinicianUiState())
    val state: StateFlow<ClinicianUiState> = _state.asStateFlow()

    // Load once when this ViewModel is first created, not on every Compose recomposition.
    init {
        loadStats()
    }

    /** Checks the prototype's fixed local key and invokes onSuccess when it matches. */
    fun validateAccessKey(value: String, onSuccess: () -> Unit) {
        if (value.trim() == ACCESS_KEY) {
            _state.value = _state.value.copy(accessErrorMessage = "")
            onSuccess()
        } else {
            _state.value = _state.value.copy(accessErrorMessage = "Incorrect clinician access key")
        }
    }

    /** Loads a one-time Room aggregate snapshot and exposes loading or failure state. */
    fun loadStats() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isStatsLoading = true, statsErrorMessage = "")
            // runCatching maps exceptions to a message here; it also catches cancellation in this implementation.
            runCatching { repository.getClinicianStats() }
                .onSuccess { stats ->
                    _state.value = _state.value.copy(
                        isStatsLoading = false,
                        stats = stats
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isStatsLoading = false,
                        statsErrorMessage = "Could not load clinician statistics."
                    )
                }
        }
    }

    /** Uses the currently loaded statistics to request observations; does nothing without stats. */
    fun generateInsights() {
        val stats = _state.value.stats ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isInsightsLoading = true, insightsErrorMessage = "")
            when (val result = repository.generateClinicianInsights(stats)) {
                is ClinicianInsightsResult.Success -> {
                    _state.value = _state.value.copy(
                        isInsightsLoading = false,
                        insights = result.insights
                    )
                }

                is ClinicianInsightsResult.Error -> {
                    _state.value = _state.value.copy(
                        isInsightsLoading = false,
                        insightsErrorMessage = result.message
                    )
                }
            }
        }
    }

    private companion object {
        const val ACCESS_KEY = "DEMO_ONLY_ACCESS"
    }
}

/** Snapshot of access feedback, aggregate loading, and generated observations for the dashboard. */
data class ClinicianUiState(
    val accessErrorMessage: String = "",
    val isStatsLoading: Boolean = false,
    val statsErrorMessage: String = "",
    val stats: ClinicianStats? = null,
    val isInsightsLoading: Boolean = false,
    val insightsErrorMessage: String = "",
    val insights: List<String> = emptyList()
)
