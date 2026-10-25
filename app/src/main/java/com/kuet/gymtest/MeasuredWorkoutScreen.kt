package com.kuet.gymtest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuet.gymtest.analysis.DepthStatus
import com.kuet.gymtest.session.*
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*

@Composable
fun MeasuredWorkoutScreen(exercise: Exercise, viewModel: WorkoutSessionViewModel,
    onBack: () -> Unit, onFinish: (WorkoutSummary?) -> Unit) {
    CameraSystemBars()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(exercise.name) { viewModel.open(exercise.name, exercise.defaultSets, exercise.defaultReps) }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it; requested = true
        if (!it) viewModel.pause()
    }
    var lens by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var retry by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf(PoseCameraStatus()) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var delivered by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { viewModel.pause(); camera = PoseCameraStatus(false, "Camera paused", lens) }
            if (event == Lifecycle.Event.ON_START) {
                hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (!hasCamera) viewModel.pause()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); viewModel.pause() }
    }
    val p = ui.progress ?: return
    val leave: () -> Unit = {
        if (p.status == SessionStatus.SETUP) onBack()
        else { viewModel.pause(); confirmExit = true }
    }
    BackHandler(onBack = leave)
    val progress by animateFloatAsState(p.currentReps.toFloat() / p.targetReps, label = "Set progress")
    val instruction = when {
        !hasCamera -> "Allow camera access to monitor your squat"
        !camera.ready -> camera.message
        p.status == SessionStatus.SETUP -> "Stand sideways with your head and feet visible"
        p.status == SessionStatus.PAUSED -> "Paused — Resume when you are ready"
        p.status == SessionStatus.BETWEEN_SETS -> "Set complete — press Next Set when ready"
        p.status == SessionStatus.COMPLETE -> "Workout complete — Finish to save your results"
        else -> ui.analysis?.feedback?.text ?: "Pose unclear — adjust the camera or lighting"
    }
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (hasCamera) PoseCamera(Modifier.fillMaxSize(), lens = lens, retry = retry,
            frameKey = viewModel::key, onBinding = viewModel::bindCamera,
            onPose = { viewModel.pose(it.frame) }, onStatus = { camera = it },
            onTrackingLost = viewModel::trackingLost, onInterrupted = viewModel::pause)
        CameraScrim()
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(Space.Lg), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassCard(padding = PaddingValues(Space.Xs), fill = CameraGlass,
                            border = Color.White.copy(alpha = .3f), shape = WorkoutShapes.Control) {
                            IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CameraInk) }
                        }
                        Spacer(Modifier.width(Space.Md))
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleLarge, color = CameraInk)
                            Text("Side-view squat monitoring", color = CameraInk.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(Space.Lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Md)) {
                        GlassCameraStat("${p.currentReps} / ${p.targetReps}", "Measured reps", Modifier.weight(1f))
                        GlassCameraStat("${p.currentSet} / ${p.targetSets}", "Current set", Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(Space.Xxl))
                if (!hasCamera) {
                    CameraPermissionCard({ permission.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth())
                    if (requested) TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text("Open app settings", color = CameraInk) }
                }
                Spacer(Modifier.height(Space.Xl))
                GlassCard(Modifier.fillMaxWidth(), fill = CameraGlass, border = Color.White.copy(alpha = .3f)) {
                    Text(instruction, color = CameraInk, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Space.Md))
                    Text(when {
                        p.status != SessionStatus.RUNNING -> "${p.totalReps} total reps · ${p.completedSets} completed sets"
                        ui.analysis?.trackingValid != true -> "Waiting for valid squat tracking"
                        else -> "${ui.analysis!!.phase.name.lowercase().replace('_', ' ')} · " +
                            if (ui.analysis!!.depth == DepthStatus.TARGET_REACHED) "Target bend reached" else "Depth estimate: above target"
                    }, color = CameraInk.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(Space.Md))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = TrackingGreen, trackColor = Color.White.copy(alpha = .2f))
                    Spacer(Modifier.height(Space.Md))
                    Text("2D movement estimate. Keep a side view; thresholds need device calibration.",
                        style = MaterialTheme.typography.bodySmall, color = CameraInk.copy(alpha = .8f))
                    Spacer(Modifier.height(Space.Lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Md)) {
                        OutlinedButton(onClick = {
                            when (p.status) {
                                SessionStatus.SETUP -> viewModel.start()
                                SessionStatus.RUNNING -> viewModel.pause()
                                SessionStatus.PAUSED -> viewModel.resume()
                                SessionStatus.BETWEEN_SETS -> viewModel.nextSet()
                                else -> Unit
                            }
                        }, enabled = p.status == SessionStatus.RUNNING || (hasCamera && camera.ready &&
                            p.status !in listOf(SessionStatus.COMPLETE, SessionStatus.FINISHED)),
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp), shape = WorkoutShapes.Control,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CameraInk)) {
                            Text(when (p.status) {
                                SessionStatus.SETUP -> "Start"
                                SessionStatus.RUNNING -> "Pause"
                                SessionStatus.PAUSED -> "Resume"
                                SessionStatus.BETWEEN_SETS -> "Next Set"
                                else -> "Complete"
                            })
                        }
                        WorkoutPrimaryButton("Finish", { viewModel.pause(); confirmFinish = true }, Modifier.weight(1f), icon = Icons.Default.Stop)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { viewModel.pause(); confirmReset = true }) { Text("Reset", color = CameraInk) }
                        TextButton(onClick = {
                            viewModel.pause(); camera = PoseCameraStatus(false, "Switching camera…", camera.lens)
                            lens = 1 - camera.lens
                            retry++ // Also rebind when a previous unavailable-lens request fell back.
                        }, enabled = camera.ready) {
                            Text(if (camera.lens == CameraSelector.LENS_FACING_FRONT) "Front → Rear" else "Rear → Front", color = CameraInk)
                        }
                        if (!camera.ready && hasCamera) TextButton(onClick = { viewModel.pause(); retry++ }) { Text("Retry", color = CameraInk) }
                    }
                    if (camera.ready && camera.lens != lens) Text(camera.message,
                        color = CameraInk.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text("Reset this workout?") }, text = { Text("Clear all reps and sets without saving a history entry.") },
        confirmButton = { TextButton(onClick = { viewModel.reset(); confirmReset = false }) { Text("Reset workout") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }, containerColor = IvoryLight)
    if (confirmFinish) AlertDialog(onDismissRequest = { confirmFinish = false },
        title = { Text("Finish this workout?") }, text = {
            Text(if (p.totalReps == 0) "No valid repetitions completed. No history entry will be saved."
                else "${p.totalReps} measured reps · ${p.completedSets} fully completed sets.\n" +
                    "Reps by set: ${p.perSet.joinToString()}.\n" + if (p.fullyCompleted) "All targets reached." else "This workout will be saved as finished early.")
        }, confirmButton = { TextButton(onClick = {
            if (!delivered) { delivered = true; confirmFinish = false; onFinish(viewModel.finish(System.currentTimeMillis())) }
        }) { Text("Finish workout") } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } }, containerColor = IvoryLight)
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("Leave this workout?") },
        text = { Text("Discard this workout, or use Finish to save your measured repetitions.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmExit = false; confirmFinish = true }) { Text("Finish instead") } },
        containerColor = IvoryLight)
}
