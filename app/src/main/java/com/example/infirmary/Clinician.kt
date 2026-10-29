

package com.example.infirmary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.infirmary.data.ClinicianStats
import com.example.infirmary.viewmodel.ClinicianViewModel
import java.util.Locale

/** Collects a local access key and delegates the check before dashboard navigation. */
@Composable
fun ClinicianLoginScreen(
    viewModel: ClinicianViewModel,
    onAuthenticated: () -> Unit,
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var accessKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Clinician Login")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = accessKey,
            onValueChange = { accessKey = it },
            label = { Text("Access key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.accessErrorMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(state.accessErrorMessage)
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.validateAccessKey(accessKey, onAuthenticated) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Continue")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onBackClick, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
        }
    }
}

/** Displays the aggregate snapshot and requests AI observations only after statistics load. */
@Composable
fun ClinicianDashboardScreen(
    viewModel: ClinicianViewModel,
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Clinician Dashboard")
        if (state.isStatsLoading) {
            CircularProgressIndicator()
        }
        if (state.statsErrorMessage.isNotBlank()) {
            Text(state.statsErrorMessage)
        }
        state.stats?.let { stats ->
            StatsCard(stats)
        }
        Button(
            onClick = { viewModel.generateInsights() },
            enabled = !state.isInsightsLoading && state.stats != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isInsightsLoading) "Finding Patterns..." else "Find Patterns")
        }
        if (state.insightsErrorMessage.isNotBlank()) {
            Text(state.insightsErrorMessage)
        }
        if (state.insights.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("GenAI Insights")
                    state.insights.forEachIndexed { index, insight ->
                        Text("${index + 1}. $insight")
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Settings")
        }
    }
}

/** Formats the four aggregate statistics for display, using one decimal place for averages. */
@Composable
private fun StatsCard(stats: ClinicianStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Aggregate Statistics")
            Text("Total patients: ${stats.totalPatients}")
            Text(
                "Average medications per patient: ${
                    String.format(Locale.US, "%.1f", stats.averageMedicationsPerPatient)
                }"
            )
            Text("Most common symptom: ${stats.mostCommonSymptomCategory}")
            Text(
                "Average symptom severity: ${
                    String.format(Locale.US, "%.1f", stats.averageSymptomSeverity)
                } / 10"
            )
        }
    }
}
