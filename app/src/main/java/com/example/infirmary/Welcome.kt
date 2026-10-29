package com.example.infirmary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infirmary.data.MedTrackRepository
import com.example.infirmary.ui.navigation.MedTrackApp
import com.example.infirmary.ui.theme.InfirmaryTheme

/** Android entry point; all app screens are composables hosted inside this single Activity. */
class MainActivity : ComponentActivity() {
    /** Initialises the single Activity and installs the themed Compose content tree. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InfirmaryTheme {
                // Reuse dependencies across recompositions of this content; Room itself is a process singleton.
                val repository = remember { MedTrackRepository.create(applicationContext) }
                MedTrackApp(repository = repository)
            }
        }
    }
}

/** Displays the app introduction and forwards login/sign-up navigation events to its parent. */
@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Infirmary",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            fontSize = 45.sp
        )
        Text(
            text = "Your medication companion",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Image(
            painter = painterResource(id = R.drawable.infirmary),
            contentDescription = "Welcome image",
            modifier = Modifier.size(150.dp)
        )
        Text(
            text = "This app is for tracking purposes only and does not replace professional medical advice.",
            modifier = Modifier.padding(12.dp),
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Button(onClick = onLoginClick) {
            Text("Login")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSignUpClick) {
            Text("Sign Up")
        }
        Spacer(Modifier.height(12.dp))
    }
}
