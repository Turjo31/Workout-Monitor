package com.kuet.gymtest

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*

@Composable
fun CameraScreen(onBack: () -> Unit = {}) {
    CameraSystemBars()
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    ) }
    var tracking by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(PoseCameraStatus()) }
    var lens by remember { mutableIntStateOf(androidx.camera.core.CameraSelector.LENS_FACING_FRONT) }
    var retry by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) permission.launch(Manifest.permission.CAMERA) }
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (hasCamera) PoseCamera(Modifier.fillMaxSize(), lens = lens, retry = retry,
            onStatus = { camera = it }, onTrackingLost = { tracking = false }, onInterrupted = { tracking = false }, onPose = {
            tracking = it.landmarks.count { landmark -> (landmark.visibility ?: 0f) > .5f } >= 8
        })
        CameraScrim()
        Row(Modifier.statusBarsPadding().padding(Space.Lg), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CameraInk) }
            Text("Pose preview", color = CameraInk, style = MaterialTheme.typography.headlineSmall)
        }
        if (!hasCamera) CameraPermissionCard(
            { permission.launch(Manifest.permission.CAMERA) },
            Modifier.align(Alignment.Center).padding(Space.Xl)
        )
        GlassCard(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Space.Xl).fillMaxWidth(),
            fill = CameraGlass, border = GlassBorder.copy(alpha = .3f)
        ) {
            Icon(Icons.Default.CheckCircleOutline, null, tint = TrackingGreen)
            Text(if (!camera.ready) camera.message else if (tracking) "Pose detected" else "Find a little room to move",
                color = CameraInk, style = MaterialTheme.typography.titleLarge)
            Text("Stand back until your full body is visible. The overlay follows your movement.",
                color = CameraInk.copy(alpha = .85f), style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { lens = 1 - camera.lens }, enabled = camera.ready) {
                    Text(if (camera.lens == 0) "Front → Rear" else "Rear → Front", color = CameraInk)
                }
                if (!camera.ready && hasCamera) TextButton(onClick = { retry++ }) { Text("Retry camera", color = CameraInk) }
            }
        }
    }
}
