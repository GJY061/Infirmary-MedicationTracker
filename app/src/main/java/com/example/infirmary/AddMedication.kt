
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.infirmary.viewmodel.MedicationViewModel
import java.util.Locale

/** Collects medication details, formats the selected time, and delegates saving to the ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationScreen(
    viewModel: MedicationViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.formState.collectAsState()
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    // derivedStateOf recalculates HH:mm when the TimePicker hour/minute changes.
    // The fixed-width text also sorts chronologically in the medication DAO query.
    val timeText by remember {
        derivedStateOf { String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add Medication")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Medication Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = dosage,
            onValueChange = { dosage = it },
            label = { Text("Dosage (e.g., 250mg)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SimpleDropdown(
            label = "Frequency",
            value = frequency,
            options = listOf("Once daily", "Twice daily", "Three times daily", "As needed"),
            onValueChange = { frequency = it }
        )
        Text("Scheduled Time: $timeText")
        TimePicker(state = timePickerState)
        SimpleDropdown(
            label = "Medication Type",
            value = type,
            options = listOf("Tablet", "Capsule", "Liquid", "Injection", "Topical", "Other"),
            onValueChange = { type = it }
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        if (state.errorMessage.isNotBlank()) Text(state.errorMessage)
        if (state.infoMessage.isNotBlank()) Text(state.infoMessage)
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    viewModel.saveMedication(name, dosage, frequency, timeText, type, notes, onSaved)
                },
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.isSaving) "Saving..." else "Save")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Reusable read-only selection field; the caller owns the selected value through onValueChange. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDropdown(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
