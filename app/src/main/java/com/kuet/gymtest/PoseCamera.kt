package com.kuet.gymtest

import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun PoseCamera(
    modifier: Modifier = Modifier,
    onPose: (PoseResult) -> Unit = {}
) {
    val context = LocalContext.current
    var pose by remember { mutableStateOf<PoseResult?>(null) }
    val currentOnPose by rememberUpdatedState(onPose)
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val active = remember { AtomicBoolean(true) }

    val detector = remember {
        PoseDetector(context) {
            mainExecutor.execute {
                if (active.get()) {
                    pose = it
                    currentOnPose(it)
                }
            }
        }
    }

    DisposableEffect(detector) {
        onDispose {
            active.set(false)
            detector.close()
        }
    }

    val analyzer = remember(detector) {
        ImageAnalysis.Analyzer { image -> detector.detect(image, isFrontCamera = true) }
    }

    Box(modifier = modifier) {
        CameraPreview(modifier = Modifier.fillMaxSize(), analyzer = analyzer)
        PoseOverlay(pose = pose, modifier = Modifier.fillMaxSize())
    }
}
