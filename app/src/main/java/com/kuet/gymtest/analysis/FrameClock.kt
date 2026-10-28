package com.kuet.gymtest.analysis

/** Preserves capture intervals without assuming the camera shares the app clock origin. */
class FrameClock(private val maximumDriftMs: Long = 1000) {
    private var sourceAnchor: Long? = null
    private var receiptAnchor = 0L
    private var lastSource = -1L
    private var lastReceipt = -1L
    private var lastTask = -1L
    fun timestamp(sourceNs: Long, receiptMs: Long): Long? {
        require(sourceNs >= 0 && receiptMs >= 0) { "Invalid camera timestamp" }
        if (sourceAnchor == null) { sourceAnchor = sourceNs; receiptAnchor = receiptMs }
        if (sourceNs == lastSource) return null
        require(sourceNs > lastSource) { "Camera clock moved backwards — retry camera" }
        if (lastReceipt >= 0) require(kotlin.math.abs((sourceNs - lastSource) / 1_000_000 - (receiptMs - lastReceipt)) <= maximumDriftMs) {
            "Camera clock discontinuity — retry camera"
        }
        val task = receiptAnchor + (sourceNs - sourceAnchor!!) / 1_000_000
        lastSource = sourceNs; lastReceipt = receiptMs
        if (task <= lastTask) return null
        lastTask = task
        return task
    }
}
