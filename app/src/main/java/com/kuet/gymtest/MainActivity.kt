package com.kuet.gymtest

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import com.kuet.gymtest.session.WorkoutSessionViewModel
import com.kuet.gymtest.ui.components.AppBackground
import com.kuet.gymtest.ui.components.LocalGlassBackdrop
import com.kuet.gymtest.ui.components.WorkoutBottomNavigation
import com.kuet.gymtest.ui.theme.GymTestTheme

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Plan("Plan", Icons.Outlined.FitnessCenter),
    Exercises("Exercises", Icons.AutoMirrored.Outlined.ViewList),
    Profile("Profile", Icons.Outlined.Person)
}

class MainActivity : ComponentActivity() {
    private val workoutSession by viewModels<WorkoutSessionViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        setContent {
            GymTestTheme { AppBackground { AppNavigation(workoutSession) } }
        }
    }
}

@Composable
fun AppNavigation(workoutSession: WorkoutSessionViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var profile by rememberSaveable(stateSaver = ProfileStateSaver) { mutableStateOf<UserProfile?>(null) }
    var activeExerciseName by rememberSaveable { mutableStateOf<String?>(null) }
    var history by rememberSaveable(stateSaver = HistoryStateSaver) { mutableStateOf(emptyList<WorkoutRecord>()) }
    var planNames by rememberSaveable(stateSaver = PlanNamesStateSaver) { mutableStateOf(emptyList<String>()) }
    var showPosePreview by rememberSaveable { mutableStateOf(false) }
    val catalogue = remember { RecommendationEngine.getAllExercises() }
    val plan = planNames.mapNotNull { name -> catalogue.find { it.name == name } }
    val exercise = catalogue.find { it.name == activeExerciseName }
    val currentProfile = profile
    val screenState = rememberSaveableStateHolder()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val startExercise: (Exercise) -> Unit = { workoutSession.discard(); activeExerciseName = it.name }
    val togglePlan: (Exercise) -> Unit = { selected ->
        planNames = if (selected.name in planNames) planNames - selected.name else planNames + selected.name
    }

    when {
        currentProfile == null -> OnboardingScreen {
            profile = it
            tab = Tab.Home
        }
        exercise != null -> {
            BackHandler { workoutSession.discard(); activeExerciseName = null }
            CompositionLocalProvider(LocalGlassBackdrop provides null) {
                WorkoutSessionScreen(
                    exercise = exercise,
                    viewModel = workoutSession,
                    onBack = { workoutSession.discard(); activeExerciseName = null },
                    onFinish = { summary ->
                        if (activeExerciseName != null) {
                            val record = if (exercise.name == "Bodyweight Squat") summary?.let(WorkoutRecord::measured)
                                else WorkoutRecord(System.currentTimeMillis(), exercise.name,
                                    java.util.UUID.randomUUID().toString(), WorkoutRecordKind.DEMO)
                            if (record != null) history = WorkoutHistoryCodec.appendOnce(history, record)
                            workoutSession.discard()
                            activeExerciseName = null
                        }
                    }
                )
            }
        }
        showPosePreview -> {
            BackHandler { showPosePreview = false }
            CompositionLocalProvider(LocalGlassBackdrop provides null) {
                CameraScreen(onBack = { showPosePreview = false })
            }
        }
        else -> {
            BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { if (!keyboardVisible) WorkoutBottomNavigation(tab) { tab = it } }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    screenState.SaveableStateProvider(tab.name) {
                        when (tab) {
                            Tab.Home -> HomeScreen(currentProfile, plan, history, { tab = it }, startExercise)
                            Tab.Plan -> RecommendationScreen(
                                currentProfile, plan, { tab = Tab.Profile }, togglePlan, startExercise
                            )
                            Tab.Exercises -> ExercisePoolScreen(onStart = startExercise)
                            Tab.Profile -> ProfileScreen(
                                initial = currentProfile, history = history, plan = plan,
                                onOpenPlan = { tab = Tab.Plan },
                                onCamera = { showPosePreview = true },
                                onSubmit = {
                                    profile = it
                                    val eligible = RecommendationEngine.matchingExercises(it).map(Exercise::name).toSet()
                                    planNames = planNames.filter { name -> name in eligible }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
