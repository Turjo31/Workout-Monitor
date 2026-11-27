package com.kuet.gymtest

/** Two sampled frames must agree before the visible warning changes. */
class FlareDecisionGate(private val threshold: Float = 0.60f, private val samplesRequired: Int = 2) {
    private var positive = 0
    private var negative = 0
    var confirmed = false
        private set

    fun observe(score: Float): Boolean? {
        if (!score.isFinite()) return null
        if (score >= threshold) {
            positive++
            negative = 0
            if (!confirmed && positive >= samplesRequired) {
                confirmed = true
                return true
            }
        } else {
            negative++
            positive = 0
            if (confirmed && negative >= samplesRequired) {
                confirmed = false
                return false
            }
        }
        return null
    }
}
