package com.kuet.gymtest.analysis

import kotlin.math.abs

/** One scan request per abnormal episode; the same segment must deviate in three pose frames. */
internal class GeometryAnomalyGate(segmentCount: Int) {
    data class Trigger(val segmentIndex: Int, val baselinePx: Double, val currentPx: Double,
                       val change: Double)

    private val baseline = DoubleArray(segmentCount)
    private val consecutive = IntArray(segmentCount)
    private var episodeActive = false
    private var cleanFrames = 0

    fun observe(lengths: DoubleArray): Trigger? {
        require(lengths.size == baseline.size)
        var observed = false
        var anyAbnormal = false
        var trigger: Trigger? = null
        for (index in lengths.indices) {
            val current = lengths[index]
            if (!current.isFinite() || current < 8.0) {
                consecutive[index] = 0
                continue
            }
            observed = true
            val reference = baseline[index]
            if (reference == 0.0) {
                baseline[index] = current
                continue
            }
            val change = (current - reference) / reference
            if (abs(change) > .30) {
                anyAbnormal = true
                consecutive[index]++
                if (consecutive[index] >= 3 &&
                    (trigger == null || abs(change) > abs(trigger.change))) {
                    trigger = Trigger(index, reference, current, change)
                }
            } else {
                consecutive[index] = 0
                baseline[index] = reference * .9 + current * .1
            }
        }
        cleanFrames = if (observed && !anyAbnormal) (cleanFrames + 1).coerceAtMost(3) else 0
        if (cleanFrames >= 3) episodeActive = false
        if (episodeActive || trigger == null) return null
        episodeActive = true
        return trigger
    }
}
