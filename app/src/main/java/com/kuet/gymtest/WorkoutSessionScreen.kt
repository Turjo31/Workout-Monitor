package com.kuet.gymtest

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*
import kotlinx.coroutines.delay
import com.kuet.gymtest.session.WorkoutSessionViewModel
import com.kuet.gymtest.session.WorkoutSummary
import com.kuet.gymtest.analysis.MeasuredExercises

@Composable
fun WorkoutSessionScreen(exercise: Exercise, viewModel: WorkoutSessionViewModel,
    onBack: () -> Unit, onFinish: (WorkoutSummary?) -> Unit) {
    if (MeasuredExercises.supports(exercise.name)) {
        MeasuredWorkoutScreen(exercise, viewModel, onBack, onFinish)
        return
    }
    CameraSystemBars()
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    ) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) permission.launch(Manifest.permission.CAMERA) }
    var currentSet by rememberSaveable(exercise.name) { mutableIntStateOf(1) }
    var reps by rememberSaveable(exercise.name) { mutableIntStateOf(0) }
    var isPaused by rememberSaveable(exercise.name) { mutableStateOf(false) }
    var completed by rememberSaveable(exercise.name) { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(false) }
    var cameraStatus by remember { mutableStateOf(PoseCameraStatus()) }
    val totalSets = exercise.defaultSets
    val targetReps = exercise.defaultReps
    val progress by animateFloatAsState((reps.toFloat() / targetReps).coerceIn(0f, 1f), label = "Set progress")

    // Existing timer-based session behavior is retained; live landmarks are independent.
    LaunchedEffect(isPaused, currentSet) {
        if (isPaused || completed) return@LaunchedEffect
        while (reps < targetReps) {
            delay(1500)
            reps++
        }
        if (currentSet < totalSets) {
            delay(1000)
            currentSet++
            reps = 0
        } else completed = true
    }
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (hasCamera) PoseCamera(
            modifier = Modifier.fillMaxSize(),
            onStatus = { cameraStatus = it }, onTrackingLost = { tracking = false },
            onPose = { result -> tracking = result.landmarks.count { (it.visibility ?: 0f) > .5f } >= 8 }
        )
        CameraScrim()
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).padding(Space.Lg),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassCard(
                            padding = PaddingValues(Space.Xs), fill = CameraGlass,
                            border = Color.White.copy(alpha = .3f), shape = WorkoutShapes.Control
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CameraInk)
                            }
                        }
                        Spacer(Modifier.width(Space.Md))
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleLarge, color = CameraInk)
                            Text(
                                "Live pose monitoring", color = CameraInk.copy(alpha = .82f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.Lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Md)) {
                        GlassCameraStat("$reps / $targetReps", "Timed reps · demo", Modifier.weight(1f))
                        GlassCameraStat("$currentSet / $totalSets", "Current set", Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(Space.Xxl))
                if (!hasCamera) CameraPermissionCard(
                    { permission.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.Xl))
                GlassCard(
                    Modifier.fillMaxWidth(), fill = CameraGlass, border = Color.White.copy(alpha = .3f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when {
                                completed -> Icons.Default.CheckCircle
                                isPaused -> Icons.Default.Pause
                                tracking -> Icons.Default.CheckCircleOutline
                                else -> Icons.Default.PersonOutline
                            },
                            null, tint = TrackingGreen
                        )
                        Spacer(Modifier.width(Space.Md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    completed -> "Session complete"
                                    isPaused -> "Session paused"
                                    !cameraStatus.ready -> cameraStatus.message
                                    tracking -> "Pose detected"
                                    else -> "Step into the frame"
                                },
                                color = CameraInk, style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (completed) "Tap Finish to save this session." else "Keep your full body visible.",
                                color = CameraInk.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.Md))
                    LinearProgressIndicator(
                        progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = TrackingGreen, trackColor = Color.White.copy(alpha = .2f)
                    )
                    Spacer(Modifier.height(Space.Md))
                    Text(
                        "Timed demo — movement is not counted. Live pose overlay only.",
                        style = MaterialTheme.typography.bodySmall, color = CameraInk.copy(alpha = .8f)
                    )
                    Spacer(Modifier.height(Space.Lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Md)) {
                        OutlinedButton(
                            onClick = { isPaused = !isPaused }, enabled = !completed,
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp), shape = WorkoutShapes.Control,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CameraInk)
                        ) {
                            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                            Spacer(Modifier.width(Space.Xs))
                            Text(if (isPaused) "Resume" else "Pause")
                        }
                        WorkoutPrimaryButton(
                            "Finish", { confirmFinish = true }, Modifier.weight(1f), icon = Icons.Default.Stop
                        )
                    }
                }
            }
        }
    }
    if (confirmFinish) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text("Finish this workout?") },
        text = { Text("Save this session to your workout history and return to your plan.") },
        confirmButton = {
            TextButton(onClick = { confirmFinish = false; onFinish(null) }) { Text("Finish workout") }
        },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } },
        containerColor = IvoryLight
    )
}
