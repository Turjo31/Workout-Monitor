package com.kuet.gymtest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Samples CameraX frames independently of the pose pending gate; inference has its own worker. */
class CameraFlareMonitor(context: Context, private val onChanged: (Boolean) -> Unit) : AutoCloseable {
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val gate = FlareDecisionGate()
    private var lastSampleMs = Long.MIN_VALUE // CameraX analyzer thread only.
    @Volatile private var disabled = false
    private var classifier: FlareClassifier? = null // Flare worker only.

    /** Called after the pose submission attempt, while CameraX still owns the proxy. */
    fun maybeSample(proxy: ImageProxy) {
        if (closed.get() || disabled) return
        val now = SystemClock.elapsedRealtime()
        if (lastSampleMs != Long.MIN_VALUE && now - lastSampleMs < 1500) return
        if (!busy.compareAndSet(false, true)) return
        lastSampleMs = now
        var thumbnail: Bitmap? = null
        try {
            val started = SystemClock.elapsedRealtimeNanos()
            val plane = proxy.planes[0]
            val source = CameraFrameConversion.convert(plane.buffer, plane.rowStride, plane.pixelStride,
                Rect(proxy.cropRect), proxy.imageInfo.rotationDegrees).bitmap
            try { thumbnail = Bitmap.createScaledBitmap(source, 160, 160, true) }
            finally { if (thumbnail !== source) source.recycle() }
            val snapshotMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            val sample = thumbnail
            worker.execute {
                try {
                    if (!closed.get()) classify(sample, snapshotMs)
                } finally {
                    sample?.recycle()
                    busy.set(false)
                }
            }
        } catch (error: Exception) {
            thumbnail?.recycle()
            busy.set(false)
            Log.w("CameraFlareTrace", "Frame snapshot unavailable", error)
        }
    }

    private fun classify(sample: Bitmap, snapshotMs: Double) {
        try {
            val active = classifier ?: FlareClassifier(appContext).also { classifier = it }
            val started = SystemClock.elapsedRealtimeNanos()
            val score = active.score(sample)
            val inferenceMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            val changed = gate.observe(score)
            if (BuildConfig.DEBUG) Log.d("CameraFlareTrace", "score=${"%.3f".format(Locale.US, score)} " +
                "confirmed=${gate.confirmed} snapshotMs=${"%.1f".format(Locale.US, snapshotMs)} " +
                "inferenceMs=${"%.1f".format(Locale.US, inferenceMs)}")
            if (changed != null && !closed.get()) onChanged(changed)
        } catch (error: Exception) {
            disabled = true
            Log.w("CameraFlareTrace", "Classifier stopped after inference failure", error)
            try { classifier?.close() } catch (closeError: Exception) {
                Log.w("CameraFlareTrace", "Classifier close failed", closeError)
            }
            classifier = null
            if (gate.confirmed && !closed.get()) onChanged(false)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.execute { classifier?.close(); classifier = null }
        worker.shutdown()
    }
}
