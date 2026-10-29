package com.example.infirmary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.infirmary.viewmodel.PatientViewModel

/** Displays the current profile and forwards logout, clinician, and trend navigation events. */
@Composable
fun SettingsScreen(
    viewModel: PatientViewModel,
    onLogout: () -> Unit,
    onClinicianLoginClick: () -> Unit,
    onSymptomTrendsClick: () -> Unit
) {
    val patient by viewModel.patient.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Settings")
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Name: ${patient?.name ?: "-"}")
                Text("Phone: ${patient?.phoneNumber ?: "-"}")
                Text("PatientID: ${patient?.patientId ?: sessionState.patientId ?: "-"}")
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.logout()
                onLogout()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onClinicianLoginClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clinician Login")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSymptomTrendsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Symptom Trends")
        }
    }
}
