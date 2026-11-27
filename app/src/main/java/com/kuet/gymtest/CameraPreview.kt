package com.kuet.gymtest

import android.util.Range
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executor

@Composable
fun CameraPreview(
    previewView: PreviewView, lens: Int, executor: Executor, analyzer: ImageAnalysis.Analyzer,
    modifier: Modifier = Modifier, onLens: (Int) -> Unit, onError: (String) -> Unit
) {
    val owner = LocalLifecycleOwner.current
    val errorCallback by rememberUpdatedState(onError)
    val lensCallback by rememberUpdatedState(onLens)
    var laidOut by remember(previewView) { mutableStateOf(false) }
    DisposableEffect(owner, previewView, lens, analyzer, laidOut) {
        if (!laidOut) return@DisposableEffect onDispose { }
        var active = true
        var provider: ProcessCameraProvider? = null
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        var preview: Preview? = null
        val analysis = ImageAnalysis.Builder().setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
        analysis.setAnalyzer(executor, analyzer)
        val future = ProcessCameraProvider.getInstance(previewView.context)
        future.addListener({
            if (active) try {
                val cameraProvider = future.get()
                provider = cameraProvider
                fun selector(facing: Int) = CameraSelector.Builder().requireLensFacing(facing).build()
                val actual = if (cameraProvider.hasCamera(selector(lens))) lens else 1 - lens
                check(cameraProvider.hasCamera(selector(actual))) { "No front or rear camera available" }
                val viewport = checkNotNull(previewView.viewPort) { "Preview not laid out — retry camera" }
                val supported = cameraProvider.getCameraInfo(selector(actual)).supportedFrameRateRanges
                val try60 = BuildConfig.DEBUG && supported.any { it.lower <= 60 && it.upper >= 60 }
                val requested = if (try60) Range(60, 60) else Range(30, 30)
                Log.d("CameraFpsTrace", "lens=$actual supported=${supported.sortedWith(compareBy({ it.lower }, { it.upper }))} requested=$requested")
                fun bindAt(rate: Range<Int>): Preview {
                    val candidate = Preview.Builder().setTargetRotation(rotation)
                        .setTargetFrameRate(rate).build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    preview = candidate
                    cameraProvider.bindToLifecycle(owner, selector(actual), UseCaseGroup.Builder()
                        .setViewPort(viewport).addUseCase(candidate).addUseCase(analysis).build())
                    return candidate
                }
                preview = try {
                    bindAt(requested)
                } catch (error: Exception) {
                    if (!try60) throw error
                    Log.w("CameraFpsTrace", "60 FPS bind failed; retrying 30 FPS", error)
                    preview?.let { cameraProvider.unbind(it, analysis) }
                    bindAt(Range(30, 30))
                }
                lensCallback(actual)
            } catch (error: Exception) { errorCallback("Camera unavailable: ${error.message ?: "retry camera"}") }
        }, ContextCompat.getMainExecutor(previewView.context))
        onDispose {
            active = false
            analysis.clearAnalyzer()
            preview?.let { provider?.unbind(it, analysis) }
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier.onSizeChanged { laidOut = it.width > 0 && it.height > 0 })
}
