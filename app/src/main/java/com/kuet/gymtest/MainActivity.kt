package com.kuet.gymtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kuet.gymtest.ui.theme.GymTestTheme

sealed interface Screen {
    data object Profile : Screen
    data object Recommendations : Screen
    data class Workout(val exercise: Exercise) : Screen
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
    var screen by remember { mutableStateOf<Screen>(Screen.Profile) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }

    BackHandler(enabled = screen !is Screen.Profile) {
        screen = when (screen) {
            is Screen.Workout -> Screen.Recommendations
            else -> Screen.Profile
        }
    }

    when (val current = screen) {
        Screen.Profile -> ProfileScreen(
            initial = profile,
            onSubmit = {
                profile = it
                screen = Screen.Recommendations
            }
        )

        Screen.Recommendations -> RecommendationScreen(
            profile = profile!!,
            onBack = { screen = Screen.Profile },
            onStart = { screen = Screen.Workout(it) }
        )

        is Screen.Workout -> WorkoutSessionScreen(
            exercise = current.exercise,
            onBack = { screen = Screen.Recommendations },
            onFinish = { screen = Screen.Recommendations }
        )
    }
}