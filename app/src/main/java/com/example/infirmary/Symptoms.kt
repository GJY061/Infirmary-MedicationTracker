package com.example.infirmary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.infirmary.data.SymptomEntity
import com.example.infirmary.viewmodel.SymptomViewModel

/** Combines a symptom-entry form with reactive Room history from SymptomViewModel. */
@Composable
fun SymptomsScreen(viewModel: SymptomViewModel) {
    // Observe ViewModel state so loading, messages, and data changes trigger recomposition.
    val symptoms by viewModel.symptoms.collectAsState()
    val formState by viewModel.formState.collectAsState()
    var category by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(5f) }
    var notes by remember { mutableStateOf("") }
    val (severityLabel, severityColor) = severityLabelAndColor(severity.toInt())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Log Symptom") }
        item {
            SimpleDropdown(
                label = "Category",
                value = category,
                options = listOf("Pain", "Nausea", "Dizziness", "Fatigue", "Headache", "Skin Reaction", "Other"),
                onValueChange = { category = it }
            )
        }
        item { Text("Severity: ${severity.toInt()} ($severityLabel)", color = severityColor) }
        item {
            Slider(
                value = severity,
                onValueChange = { severity = it },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = severityColor,
                    activeTrackColor = severityColor,
                    inactiveTrackColor = severityColor.copy(alpha = 0.35f)
                )
            )
        }
        item {
            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 200) notes = it },
                label = { Text("Additional notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Text("${notes.length}/200") }
        if (formState.errorMessage.isNotBlank()) item { Text(formState.errorMessage) }
        if (formState.infoMessage.isNotBlank()) item { Text(formState.infoMessage) }
        item {
            Button(
                onClick = {
                    viewModel.saveSymptom(category, severity.toInt(), notes)
                    // The current UI clears input immediately after submission, before the coroutine finishes saving.
                    if (category.isNotBlank()) {
                        category = ""
                        severity = 5f
                        notes = ""
                    }
                },
                enabled = !formState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (formState.isSaving) "Saving..." else "Save Symptom")
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item { Text("Symptom History") }
        if (viewModel.patientId.isNullOrBlank()) {
            item { Text("No logged-in patient found. Please log in again.") }
        } else if (symptoms.isEmpty()) {
            item { Text("No symptoms logged yet.") }
        } else {
            items(symptoms, key = { it.id }) { symptom ->
                SymptomRow(symptom = symptom)
            }
        }
    }
}

/** Displays one saved symptom using the same severity label/colour mapping as the entry form. */
@Composable
private fun SymptomRow(symptom: SymptomEntity) {
    val (label, color) = severityLabelAndColor(symptom.severity)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = symptom.category)
        Text(text = "Date/Time: ${symptom.dateTime}")
        Text(text = "Severity: ${symptom.severity} ($label)", color = color)
        if (symptom.notes.isNotBlank()) Text(text = "Notes: ${symptom.notes}")
    }
}

/** Maps severity 1-3 to mild, 4-6 to moderate, and all other values to severe. */
private fun severityLabelAndColor(severity: Int): Pair<String, Color> = when (severity) {
    in 1..3 -> "Mild" to Color(0xFF2E7D32)
    in 4..6 -> "Moderate" to Color(0xFFF9A825)
    else -> "Severe" to Color(0xFFC62828)
}
