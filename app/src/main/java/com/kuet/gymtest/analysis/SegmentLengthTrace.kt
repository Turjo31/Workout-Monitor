package com.kuet.gymtest.analysis

import kotlin.math.min
import kotlin.math.round

/** Debug-only caller owns this state; measurements never affect repetition decisions. */
internal class SegmentLengthTrace {
    private data class Segment(val name: String, val first: Int, val second: Int)
    private val segments = listOf(
        Segment("leftUpperArm", 11, 13), Segment("leftForearm", 13, 15),
        Segment("rightUpperArm", 12, 14), Segment("rightForearm", 14, 16),
        Segment("leftTorso", 11, 23), Segment("rightTorso", 12, 24),
        Segment("leftThigh", 23, 25), Segment("rightThigh", 24, 26),
        Segment("leftShin", 25, 27), Segment("rightShin", 26, 28)
    )
    private var previousKey: FrameKey? = null
    private var previousTimeMs: Long? = null
    private var previousLengths: List<Double?>? = null

    fun reset() {
        previousKey = null
        previousTimeMs = null
        previousLengths = null
    }

    fun observe(frame: PoseFrame): String {
        val oldKey = previousKey
        val dt = previousTimeMs?.let { frame.timestampMs - it }
        val adjacent = oldKey != null && oldKey.sessionId == frame.key.sessionId &&
            oldKey.cameraEpoch == frame.key.cameraEpoch && oldKey.analysisEpoch == frame.key.analysisEpoch &&
            frame.key.sequence > oldKey.sequence && dt != null && dt in 1..300
        val lengths = segments.map { segment ->
            val first = frame.landmarks.getOrNull(segment.first)
            val second = frame.landmarks.getOrNull(segment.second)
            val a = first?.let { PoseGeometry.point(it, frame.imageWidth, frame.imageHeight) }
            val b = second?.let { PoseGeometry.point(it, frame.imageWidth, frame.imageHeight) }
            if (a == null || b == null) null else PoseGeometry.distance(a, b)
        }
        val details = segments.indices.joinToString(";") { index ->
            val segment = segments[index]
            val first = frame.landmarks.getOrNull(segment.first)
            val second = frame.landmarks.getOrNull(segment.second)
            val length = lengths[index]
            val before = if (adjacent) previousLengths?.get(index) else null
            val change = if (length != null && before != null && before > 1e-6)
                (length - before) / before * 100.0 else null
            val visibility = if (first?.visibility != null && second?.visibility != null)
                min(first.visibility, second.visibility).takeIf { it.isFinite() } else null
            val presence = if (first?.presence != null && second?.presence != null)
                min(first.presence, second.presence).takeIf { it.isFinite() } else null
            "${segment.name}Px=${length?.let { short(it, 10.0) }}" +
                ",deltaPct=${change?.let { signed(it) }}" +
                ",vMin=${visibility?.let { short(it.toDouble(), 1000.0) }}" +
                ",pMin=${presence?.let { short(it.toDouble(), 1000.0) }}"
        }
        previousKey = frame.key
        previousTimeMs = frame.timestampMs
        previousLengths = lengths
        return "segmentDt=$dt image=${frame.imageWidth}x${frame.imageHeight} segments=$details"
    }

    private fun short(value: Double, scale: Double) = round(value * scale) / scale
    private fun signed(value: Double) = (if (value >= 0) "+" else "") + short(value, 10.0)
}
