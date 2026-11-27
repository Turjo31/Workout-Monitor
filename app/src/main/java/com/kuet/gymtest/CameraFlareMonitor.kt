package com.kuet.gymtest

import android.graphics.Bitmap
import android.util.Log
import com.kuet.gymtest.analysis.BrightStreakDetector
import com.kuet.gymtest.analysis.GeometryAnomalyGate
import com.kuet.gymtest.analysis.PoseLandmark
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

/** Scans once at startup, then only a candidate ROI or after sustained landmark distortion. */
internal class CameraFlareMonitor {
    private val segments = arrayOf(11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 23, 12 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28)
    private val segmentNames = arrayOf("leftShoulder-Elbow", "leftElbow-Wrist",
        "rightShoulder-Elbow", "rightElbow-Wrist", "leftShoulder-Hip", "rightShoulder-Hip",
        "leftHip-Knee", "leftKnee-Ankle", "rightHip-Knee", "rightKnee-Ankle")
    private val geometryGate = GeometryAnomalyGate(segments.size)
    private var fullScanRequested = true
    private var candidate: BrightStreakDetector.Region? = null
    private var confirmations = 0
    private var nextFullScanMs = 0L
    private var warningUntilMs = 0L

    fun onFrame(bitmap: Bitmap, nowMs: Long): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val prior = candidate
        if (prior != null) {
            val roi = prior.expanded(max(24, min(width, height) / 16), width, height)
            val found = find(bitmap, roi)
            if (found != null && roi.containsCenter(found)) {
                confirmations++
                candidate = found
                if (confirmations >= 3) {
                    warningUntilMs = nowMs + 4_000
                    nextFullScanMs = nowMs + 1_500
                    fullScanRequested = true // Recheck confirmed glare at a bounded interval if it persists.
                    candidate = null
                    confirmations = 0
                    Log.d("CameraFlareTrace", "confirmed bright streak; warning shown")
                }
            } else {
                candidate = null
                confirmations = 0
                Log.d("CameraFlareTrace", "bright candidate rejected")
            }
        } else if (fullScanRequested && nowMs >= nextFullScanMs) {
            fullScanRequested = false
            nextFullScanMs = nowMs + 1_500
            candidate = find(bitmap, BrightStreakDetector.Region(0, 0, width, height))
            confirmations = if (candidate == null) 0 else 1
            Log.d("CameraFlareTrace", if (candidate == null) "full scan: no streak" else "full scan: candidate")
        }
        return nowMs < warningUntilMs
    }

    fun onPose(landmarks: List<PoseLandmark>, width: Int, height: Int) {
        val lengths = DoubleArray(segments.size) { Double.NaN }
        for ((index, segment) in segments.withIndex()) {
            val a = landmarks.getOrNull(segment.first)
            val b = landmarks.getOrNull(segment.second)
            if (a == null || b == null || (a.visibility ?: 0f) < .5f || (b.visibility ?: 0f) < .5f ||
                (a.presence ?: 0f) < .5f || (b.presence ?: 0f) < .5f) continue
            val length = hypot((a.x - b.x).toDouble() * width, (a.y - b.y).toDouble() * height)
            lengths[index] = length
        }
        val trigger = geometryGate.observe(lengths)
        if (trigger != null && !fullScanRequested && candidate == null) {
            fullScanRequested = true
            Log.d("CameraFlareTrace", String.format(Locale.US,
                "sustained geometry anomaly segment=%s baselinePx=%.1f currentPx=%.1f change=%+.1f%%; full scan requested",
                segmentNames[trigger.segmentIndex], trigger.baselinePx, trigger.currentPx,
                trigger.change * 100))
        }
    }

    private fun find(bitmap: Bitmap, region: BrightStreakDetector.Region): BrightStreakDetector.Region? {
        val pixels = IntArray(region.width * region.height)
        bitmap.getPixels(pixels, 0, region.width, region.left, region.top, region.width, region.height)
        return BrightStreakDetector.find(pixels, region.width, region.height, region.left, region.top,
            bitmap.width, bitmap.height)
    }
}
