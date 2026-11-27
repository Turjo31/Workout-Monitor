package com.kuet.gymtest.analysis

import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentLengthTraceTest {
    private fun frame(time: Long, sequence: Long, elbowX: Float, cameraEpoch: Long = 1): PoseFrame {
        val landmarks = MutableList(33) { PoseLandmark(.5f, .5f, 0f, .9f, .8f) }
        landmarks[11] = PoseLandmark(0f, 0f, 0f, .95f, .99f)
        landmarks[13] = PoseLandmark(elbowX, 0f, 0f, .85f, .90f)
        landmarks[15] = PoseLandmark(.2f, 0f, 0f, .75f, .80f)
        return PoseFrame(FrameKey("session", cameraEpoch, 1, sequence), time, 100, 100, landmarks)
    }

    @Test fun signedChangesUseImmediatelyPreviousProcessedFrame() {
        val trace = SegmentLengthTrace()
        val first = trace.observe(frame(100, 1, .1f))
        assertTrue(first.contains("leftUpperArmPx=10.0,deltaPct=null"))
        val second = trace.observe(frame(150, 2, .12f))
        assertTrue(second.contains("segmentDt=50"))
        assertTrue(second.contains("leftUpperArmPx=12.0,deltaPct=+20.0"))
        assertTrue(second.contains("leftForearmPx=8.0,deltaPct=-20.0"))
        assertTrue(second.contains("vMin=0.85,pMin=0.9"))
    }

    @Test fun missingLandmarkAndNewCameraDoNotBridgeTheComparison() {
        val trace = SegmentLengthTrace()
        trace.observe(frame(100, 1, .1f))
        val missing = trace.observe(frame(150, 2, Float.NaN))
        assertTrue(missing.contains("leftUpperArmPx=null,deltaPct=null"))
        val recovered = trace.observe(frame(200, 3, .12f))
        assertTrue(recovered.contains("leftUpperArmPx=12.0,deltaPct=null"))
        val cameraChanged = trace.observe(frame(250, 4, .1f, cameraEpoch = 2))
        assertTrue(cameraChanged.contains("leftUpperArmPx=10.0,deltaPct=null"))
    }
}
