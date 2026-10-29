
package com.example.infirmary.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.infirmary.AddMedicationScreen
import com.example.infirmary.ClaimAccountScreen
import com.example.infirmary.ClinicianDashboardScreen
import com.example.infirmary.ClinicianLoginScreen
import com.example.infirmary.HomeScreen
import com.example.infirmary.LoginScreen
import com.example.infirmary.MedCoachScreen
import com.example.infirmary.SettingsScreen
import com.example.infirmary.SignUpScreen
import com.example.infirmary.SymptomTrendScreen
import com.example.infirmary.SymptomsScreen
import com.example.infirmary.WelcomeScreen
import com.example.infirmary.data.MedTrackRepository
import com.example.infirmary.viewmodel.AppViewModelFactory
import com.example.infirmary.viewmodel.ClinicianViewModel
import com.example.infirmary.viewmodel.MedCoachViewModel
import com.example.infirmary.viewmodel.MedicationViewModel
import com.example.infirmary.viewmodel.PatientViewModel
import com.example.infirmary.viewmodel.SymptomTrendViewModel
import com.example.infirmary.viewmodel.SymptomViewModel

/** All destinations in the custom in-memory navigation stack; none creates another Activity. */
private enum class AppRoute {
    Welcome,
    Login,
    ClaimAccount,
    SignUp,
    Home,
    Symptoms,
    MedCoach,
    Settings,
    AddMedication,
    ClinicianLogin,
    ClinicianDashboard,
    SymptomTrends
}

/**
 * Creates/reuses ViewModels, waits for session startup, and selects the current screen.
 * Navigation state uses remember, so this custom back stack is not saved across Activity recreation.
 */
@Composable
fun MedTrackApp(repository: MedTrackRepository) {
    // The factory injects the same repository into ViewModels obtained from the Activity owner.
    val factory = remember(repository) { AppViewModelFactory(repository) }
    val patientViewModel: PatientViewModel = viewModel(factory = factory)
    val patientSessionState by patientViewModel.sessionState.collectAsState()
    // null means startup is still pending; a spinner is shown instead of a partially initialised screen.
    var route by remember { mutableStateOf<AppRoute?>(null) }
    var sessionPatientId by remember { mutableStateOf<String?>(null) }
    val routeHistory = remember { mutableStateListOf<AppRoute>() }

    /** Pushes the previous destination onto history before displaying a different screen. */
    fun navigateTo(nextRoute: AppRoute) {
        val currentRoute = route
        if (currentRoute != null && currentRoute != nextRoute) {
            routeHistory.add(currentRoute)
        }
        route = nextRoute
    }

    /** Replaces the destination and clears history at login/logout boundaries. */
    fun clearStackAndGo(nextRoute: AppRoute) {
        routeHistory.clear()
        route = nextRoute
    }

    /** Changes the destination without adding history, used when clinician login succeeds. */
    fun replaceRoute(nextRoute: AppRoute) {
        route = nextRoute
    }

    /** Pops the most recent destination, or uses the supplied fallback if history is empty. */
    fun goBack(fallbackRoute: AppRoute? = null) {
        if (routeHistory.isNotEmpty()) {
            route = routeHistory.removeAt(routeHistory.lastIndex)
        } else if (fallbackRoute != null) {
            route = fallbackRoute
        }
    }

    // Choose the initial route only after CSV seeding and session restoration finish.
    // LaunchedEffect runs for this readiness key rather than on every recomposition.
    LaunchedEffect(patientSessionState.isReady) {
        if (patientSessionState.isReady && route == null) {
            sessionPatientId = patientSessionState.patientId
            route = if (patientSessionState.patientId == null) AppRoute.Welcome else AppRoute.Home
        }
    }

    if (route == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val loginSuccess: (String) -> Unit = { patientId ->
        sessionPatientId = patientId
        clearStackAndGo(AppRoute.Home)
    }
    val logout: () -> Unit = {
        sessionPatientId = null
        clearStackAndGo(AppRoute.Welcome)
    }

    val currentRoute = route ?: AppRoute.Welcome
    val showBottomBar = currentRoute in listOf(
        AppRoute.Home,
        AppRoute.Symptoms,
        AppRoute.MedCoach,
        AppRoute.Settings
    )

    // Intercept Android Back only when this custom stack has a destination to pop.
    BackHandler(enabled = routeHistory.isNotEmpty()) {
        goBack()
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                MainNavigationBar(
                    selectedRoute = currentRoute,
                    onRouteSelected = { navigateTo(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentRoute) {
                AppRoute.Welcome -> WelcomeScreen(
                    onLoginClick = { navigateTo(AppRoute.Login) },
                    onSignUpClick = { navigateTo(AppRoute.SignUp) }
                )

                AppRoute.Login -> LoginScreen(
                    viewModel = patientViewModel,
                    onLoginSuccess = loginSuccess,
                    onClaimClick = { navigateTo(AppRoute.ClaimAccount) },
                    onSignUpClick = { navigateTo(AppRoute.SignUp) },
                    onBackClick = { goBack(AppRoute.Welcome) }
                )

                AppRoute.ClaimAccount -> ClaimAccountScreen(
                    viewModel = patientViewModel,
                    onClaimSuccess = loginSuccess,
                    onBackClick = { goBack(AppRoute.Login) }
                )

                AppRoute.SignUp -> SignUpScreen(
                    viewModel = patientViewModel,
                    onSignUpSuccess = loginSuccess,
                    onBackClick = { goBack(AppRoute.Login) }
                )

                // Patient-specific keys select separate ViewModel instances for different accounts.
                // Their store is Activity-owned; leaving a route does not itself clear its ViewModel.
                AppRoute.Home -> key("home-$sessionPatientId") {
                    val medicationViewModel: MedicationViewModel = viewModel(
                        key = "medication-$sessionPatientId",
                        factory = factory
                    )
                    HomeScreen(
                        patientViewModel = patientViewModel,
                        medicationViewModel = medicationViewModel,
                        onAddMedicationClick = { navigateTo(AppRoute.AddMedication) }
                    )
                }

                AppRoute.Symptoms -> key("symptoms-$sessionPatientId") {
                    val viewModel: SymptomViewModel = viewModel(
                        key = "symptom-$sessionPatientId",
                        factory = factory
                    )
                    SymptomsScreen(viewModel = viewModel)
                }

                AppRoute.MedCoach -> key("medcoach-$sessionPatientId") {
                    val viewModel: MedCoachViewModel = viewModel(
                        key = "medcoach-$sessionPatientId",
                        factory = factory
                    )
                    MedCoachScreen(viewModel = viewModel)
                }

                AppRoute.Settings -> key("settings-$sessionPatientId") {
                    SettingsScreen(
                        viewModel = patientViewModel,
                        onLogout = logout,
                        onClinicianLoginClick = { navigateTo(AppRoute.ClinicianLogin) },
                        onSymptomTrendsClick = { navigateTo(AppRoute.SymptomTrends) }
                    )
                }

                // Use the same medication ViewModel key as Home to share observations and form feedback.
                AppRoute.AddMedication -> key("add-med-$sessionPatientId") {
                    val viewModel: MedicationViewModel = viewModel(
                        key = "medication-$sessionPatientId",
                        factory = factory
                    )
                    AddMedicationScreen(
                        viewModel = viewModel,
                        onSaved = { goBack(AppRoute.Home) },
                        onCancel = { goBack(AppRoute.Home) }
                    )
                }

                AppRoute.ClinicianLogin -> key("clinician-login") {
                    val viewModel: ClinicianViewModel = viewModel(
                        key = "clinician",
                        factory = factory
                    )
                    ClinicianLoginScreen(
                        viewModel = viewModel,
                        onAuthenticated = { replaceRoute(AppRoute.ClinicianDashboard) },
                        onBackClick = { goBack(AppRoute.Settings) }
                    )
                }

                // Reuse the clinician ViewModel created for login, including its loaded statistics.
                AppRoute.ClinicianDashboard -> key("clinician-dashboard") {
                    val viewModel: ClinicianViewModel = viewModel(
                        key = "clinician",
                        factory = factory
                    )
                    ClinicianDashboardScreen(
                        viewModel = viewModel,
                        onBackClick = { goBack(AppRoute.Settings) }
                    )
                }

                AppRoute.SymptomTrends -> key("symptom-trends-$sessionPatientId") {
                    val viewModel: SymptomTrendViewModel = viewModel(
                        key = "symptom-trends-$sessionPatientId",
                        factory = factory
                    )
                    SymptomTrendScreen(
                        viewModel = viewModel,
                        onBackClick = { goBack(AppRoute.Settings) }
                    )
                }
            }
        }
    }
}

/** Presentation metadata tying a bottom-bar label and icon to its destination. */
private data class NavigationTab(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector
)

/** Renders the four main tabs and forwards the selected destination to the route owner. */
@Composable
private fun MainNavigationBar(
    selectedRoute: AppRoute,
    onRouteSelected: (AppRoute) -> Unit
) {
    val items = listOf(
        NavigationTab(AppRoute.Home, "Home", Icons.Filled.Home),
        NavigationTab(AppRoute.Symptoms, "Symptoms", Icons.Filled.Favorite),
        NavigationTab(AppRoute.MedCoach, "MedCoach", Icons.Filled.Lightbulb),
        NavigationTab(AppRoute.Settings, "Settings", Icons.Filled.Settings)
    )

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.route == selectedRoute,
                onClick = { onRouteSelected(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}
