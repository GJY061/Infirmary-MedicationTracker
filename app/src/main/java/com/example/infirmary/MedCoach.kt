
package com.example.infirmary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.infirmary.viewmodel.MedCoachViewModel

/** Displays drug lookup, AI-tip generation, and saved-tip history from one ViewModel state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedCoachScreen(viewModel: MedCoachViewModel) {
    // Observe ViewModel state so loading, messages, and data changes trigger recomposition.
    val state by viewModel.state.collectAsState()
    val medicationNames by viewModel.medicationNames.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Top,
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        item { Text("MedCoach") }
        item { Spacer(Modifier.height(16.dp)) }
        item { Text("Drug Information") }
        item { Spacer(Modifier.height(8.dp)) }

        item {
            ExposedDropdownMenuBox(
                expanded = expanded && medicationNames.isNotEmpty(),
                onExpandedChange = { if (medicationNames.isNotEmpty()) expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = {
                        viewModel.updateQuery(it)
                        if (medicationNames.isNotEmpty()) expanded = true
                    },
                    label = { Text("Medication name") },
                    singleLine = true,
                    trailingIcon = {
                        if (medicationNames.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded && medicationNames.isNotEmpty(),
                    onDismissRequest = { expanded = false }
                ) {
                    // Filter the existing medication names locally; this dropdown does not make network calls.
                    val filtered = medicationNames.filter {
                        state.query.isBlank() || it.contains(state.query, ignoreCase = true)
                    }
                    filtered.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.selectMedication(name)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
        item {
            Button(
                onClick = { viewModel.lookupDrug() },
                enabled = !state.isLookupLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLookupLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Lookup Drug")
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item {
            if (state.lookupErrorMessage.isNotBlank()) {
                Text(state.lookupErrorMessage)
            } else {
                val info = state.info
                if (info != null) {
                    Text("Purpose")
                    Text(info.purpose)
                    Spacer(Modifier.height(12.dp))
                    Text("Warnings")
                    Text(info.warnings)
                    Spacer(Modifier.height(12.dp))
                    Text("Dosage and Administration")
                    Text(info.dosageAndAdministration)
                } else {
                    Text("Search for a medication to view label details.")
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
        item { Text("GenAI Medication Tips") }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Button(
                onClick = { viewModel.generateTip() },
                enabled = !state.isTipLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isTipLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Generate Personalised Tip")
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Button(
                onClick = { viewModel.showTipHistory() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show All Tips")
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            if (state.tipErrorMessage.isNotBlank()) {
                Text(state.tipErrorMessage)
            } else if (state.latestTip.isNotBlank()) {
                Text("Latest Tip")
                Spacer(Modifier.height(6.dp))
                Text(state.latestTip)
            } else {
                Text("Tap Generate Personalised Tip to receive encouragement based on your medications and recent symptoms.")
            }
        }
    }

    // Visibility is UI state; history rows remain stored in Room when the dialog closes.
    if (state.isTipHistoryVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.hideTipHistory() },
            confirmButton = {
                TextButton(onClick = { viewModel.hideTipHistory() }) {
                    Text("Close")
                }
            },
            title = { Text("Tip History") },
            text = {
                if (state.tipHistory.isEmpty()) {
                    Text("No tips generated yet.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(280.dp)
                    ) {
                        items(state.tipHistory, key = { it.id }) { tip ->
                            Column {
                                Text(tip.tipText)
                                Spacer(Modifier.height(2.dp))
                                Text("Generated: ${tip.timestamp}")
                            }
                        }
                    }
                }
            }
        )
    }
}
