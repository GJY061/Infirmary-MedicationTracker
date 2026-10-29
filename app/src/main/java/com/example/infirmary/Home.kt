
package com.example.infirmary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infirmary.data.MedicationEntity
import com.example.infirmary.viewmodel.MedicationViewModel
import com.example.infirmary.viewmodel.PatientViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Observes the patient, medications, and daily taken-status map to render the Home dashboard. */
@Composable
fun HomeScreen(
    patientViewModel: PatientViewModel,
    medicationViewModel: MedicationViewModel,
    onAddMedicationClick: () -> Unit
) {
    // Observe ViewModel state so loading, messages, and data changes trigger recomposition.
    val patient by patientViewModel.patient.collectAsState()
    val medications by medicationViewModel.medications.collectAsState()
    val takenStatuses by medicationViewModel.takenStatuses.collectAsState()
    // Count only medications in the current list whose daily status is true.
    val takenCount = medications.count { takenStatuses[it.id] == true }
    val dateText = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Hello, ${patient?.name ?: "Patient"}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            fontSize = 35.sp
        )
        Text(text = "PatientID: ${patient?.patientId ?: "-"}")
        Spacer(Modifier.height(6.dp))
        Text(text = dateText)
        Spacer(Modifier.height(16.dp))
        Text(text = "$takenCount of ${medications.size} medications taken today")
        Spacer(Modifier.height(12.dp))

        if (medicationViewModel.patientId.isNullOrBlank()) {
            Text("No logged-in patient found. Please log in again.")
        } else if (medications.isEmpty()) {
            Text("No medications scheduled.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stable database IDs keep each list item associated with its own Compose identity.
                items(medications, key = { it.id }) { medication ->
                    MedicationCard(
                        medication = medication,
                        taken = takenStatuses[medication.id] == true,
                        onTakenChanged = { medicationViewModel.setMedicationTaken(medication.id, it) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onAddMedicationClick, modifier = Modifier.fillMaxWidth()) {
            Text("Add Medication")
        }
    }
}

/** Renders one medication and forwards checkbox changes; persistence is owned by the ViewModel. */
@Composable
private fun MedicationCard(
    medication: MedicationEntity,
    taken: Boolean,
    onTakenChanged: (Boolean) -> Unit
) {
    // The card is driven entirely by the supplied status; the checkbox callback requests a write.
    val textStyle = if (taken) TextStyle(textDecoration = TextDecoration.LineThrough) else TextStyle()
    val cardColors = if (taken) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    } else {
        CardDefaults.cardColors()
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = medication.name, style = textStyle)
                Text(text = "${medication.dosage} • ${medication.frequency}", style = textStyle)
                Text(text = "Time: ${medication.scheduledTime}", style = textStyle)
                if (medication.notes.isNotBlank()) {
                    Text(text = medication.notes, style = textStyle)
                }
            }
            Checkbox(checked = taken, onCheckedChange = onTakenChanged)
        }
    }
}
