package com.kuet.gymtest

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreview(modifier: Modifier = Modifier, analyzer: ImageAnalysis.Analyzer? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(lifecycleOwner, analyzer, previewView) {
        var active = true
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = analyzer?.let { currentAnalyzer ->
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().also { it.setAnalyzer(executor, currentAnalyzer) }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (active) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    if (analysis != null) cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
                    ) else cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview
                    )
                } catch (error: Exception) {
                    error.printStackTrace()
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            analysis?.clearAnalyzer()
            if (analysis != null) provider?.unbind(preview, analysis) else provider?.unbind(preview)
        }
    }
    DisposableEffect(executor) { onDispose { executor.shutdown() } }
    AndroidView(factory = { previewView }, modifier = modifier)
}
