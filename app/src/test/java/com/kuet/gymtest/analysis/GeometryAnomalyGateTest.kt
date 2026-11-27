package com.kuet.gymtest.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryAnomalyGateTest {
    @Test fun persistentSegmentTriggersOnceAndRearmsAfterRecovery() {
        val gate = GeometryAnomalyGate(2)
        assertNull(gate.observe(doubleArrayOf(100.0, 100.0)))
        repeat(2) { assertNull(gate.observe(doubleArrayOf(150.0, 100.0))) }
        val first = gate.observe(doubleArrayOf(150.0, 100.0))
        assertNotNull(first)
        assertEquals(0, first!!.segmentIndex)
        assertEquals(100.0, first.baselinePx, .001)
        assertEquals(150.0, first.currentPx, .001)
        assertEquals(.50, first.change, .001)
        repeat(5) { assertNull(gate.observe(doubleArrayOf(150.0, 100.0))) }
        repeat(3) { assertNull(gate.observe(doubleArrayOf(100.0, 100.0))) }
        repeat(2) { assertNull(gate.observe(doubleArrayOf(150.0, 100.0))) }
        assertNotNull(gate.observe(doubleArrayOf(150.0, 100.0)))
    }

    @Test fun alternatingAndMissingSegmentsDoNotMakeThreeFrameTrigger() {
        val gate = GeometryAnomalyGate(2)
        gate.observe(doubleArrayOf(100.0, 100.0))
        repeat(4) {
            assertNull(gate.observe(doubleArrayOf(150.0, 100.0)))
            assertNull(gate.observe(doubleArrayOf(100.0, 150.0)))
        }
        assertNull(gate.observe(doubleArrayOf(150.0, 100.0)))
        assertNull(gate.observe(doubleArrayOf(Double.NaN, 100.0)))
        assertNull(gate.observe(doubleArrayOf(150.0, 100.0)))
    }
}
