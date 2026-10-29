
package com.example.infirmary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.infirmary.viewmodel.PatientViewModel

/** Collects authentication feedback and submits credentials to PatientViewModel. */
@Composable
fun LoginScreen(
    viewModel: PatientViewModel,
    onLoginSuccess: (String) -> Unit,
    onClaimClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onBackClick: () -> Unit
) {
    // Observe ViewModel state so loading, messages, and data changes trigger recomposition.
    val state by viewModel.authState.collectAsState()
    // remember preserves input across recomposition, but not Activity recreation.
    var patientId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthFormContainer(title = "Login") {
        OutlinedTextField(
            value = patientId,
            onValueChange = { patientId = it },
            label = { Text("PatientID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        AuthMessages(error = state.errorMessage, info = state.infoMessage)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.login(patientId, password, onLoginSuccess) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isLoading) "Checking..." else "Login")
        }
        TextButton(onClick = onClaimClick) {
            Text("Claim seeded account")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBackClick, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            OutlinedButton(onClick = onSignUpClick, modifier = Modifier.weight(1f)) {
                Text("Sign Up")
            }
        }
    }
}

/** Collects a seeded patient ID, matching phone number, and new password to claim an account. */
@Composable
fun ClaimAccountScreen(
    viewModel: PatientViewModel,
    onClaimSuccess: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val state by viewModel.authState.collectAsState()
    var patientId by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthFormContainer(title = "Claim Account") {
        Text("Use your seeded PatientID and phone number to set a password.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = patientId,
            onValueChange = { patientId = it },
            label = { Text("PatientID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("New Password") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        AuthMessages(error = state.errorMessage, info = state.infoMessage)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.claim(patientId, phoneNumber, password, onClaimSuccess) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isLoading) "Claiming..." else "Set Password")
        }
        TextButton(onClick = onBackClick) {
            Text("Back to Login")
        }
    }
}

/** Provides the shared centred layout and title around login, claim, and sign-up content. */
@Composable
fun AuthFormContainer(
    title: String,
    content: @Composable ColumnScopeMarker.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title)
        Spacer(Modifier.height(16.dp))
        ColumnScopeMarker.content()
    }
}

/** Receiver used by AuthFormContainer's content lambda; it is not Compose's actual ColumnScope. */
object ColumnScopeMarker

/** Displays nonblank error and information messages supplied by authentication state. */
@Composable
fun AuthMessages(error: String, info: String) {
    if (error.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(error)
    }
    if (info.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(info)
    }
}
