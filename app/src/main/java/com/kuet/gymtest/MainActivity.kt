package com.kuet.gymtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kuet.gymtest.ui.theme.DeepBlue
import com.kuet.gymtest.ui.theme.GymTestTheme
import com.kuet.gymtest.ui.theme.LightBlue

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Plan("Plan", Icons.Default.FitnessCenter),
    Camera("Camera", Icons.Default.CameraAlt),
    Profile("Profile", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var tab by remember { mutableStateOf(Tab.Home) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var activeExercise by remember { mutableStateOf<Exercise?>(null) }
    var history by remember { mutableStateOf(listOf<WorkoutRecord>()) }

    val currentProfile = profile
    val exercise = activeExercise

    when {
        currentProfile == null -> {
            OnboardingScreen(
                onComplete = {
                    profile = it
                    tab = Tab.Home
                }
            )
        }

        exercise != null -> {
            BackHandler { activeExercise = null }
            WorkoutSessionScreen(
                exercise = exercise,
                onBack = { activeExercise = null },
                onFinish = {
                    history = history + WorkoutRecord(System.currentTimeMillis(), exercise.name)
                    activeExercise = null
                }
            )
        }

        else -> {
            BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepBlue,
                                    selectedTextColor = DeepBlue,
                                    indicatorColor = LightBlue,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when (tab) {
                        Tab.Home -> HomeScreen(
                            profile = currentProfile,
                            history = history,
                            onNavigate = { tab = it },
                            onStart = { activeExercise = it }
                        )

                        Tab.Plan -> RecommendationScreen(
                            profile = currentProfile,
                            onSetupProfile = { tab = Tab.Profile },
                            onStart = { activeExercise = it }
                        )

                        Tab.Camera -> CameraScreen()

                        Tab.Profile -> ProfileScreen(
                            initial = currentProfile,
                            onSubmit = {
                                profile = it
                                tab = Tab.Plan
                            }
                        )
                    }
                }
            }
        }
    }
}