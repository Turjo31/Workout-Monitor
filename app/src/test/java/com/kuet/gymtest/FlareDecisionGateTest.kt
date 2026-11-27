package com.kuet.gymtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlareDecisionGateTest {
    @Test fun warningNeedsConsecutivePositiveSamplesAndClearsAfterConsecutiveNegatives() {
        val gate = FlareDecisionGate()
        assertNull(gate.observe(.8f))
        assertFalse(gate.confirmed)
        assertNull(gate.observe(.3f))
        assertNull(gate.observe(.6f))
        assertEquals(true, gate.observe(.7f))
        assertTrue(gate.confirmed)
        assertNull(gate.observe(.2f))
        assertTrue(gate.confirmed)
        assertNull(gate.observe(.9f))
        assertNull(gate.observe(.1f))
        assertEquals(false, gate.observe(.2f))
        assertFalse(gate.confirmed)
    }

    @Test fun nonFiniteScoreDoesNotChangeWarning() {
        val gate = FlareDecisionGate()
        assertNull(gate.observe(Float.NaN))
        assertNull(gate.observe(.9f))
        assertEquals(true, gate.observe(.9f))
        assertNull(gate.observe(Float.POSITIVE_INFINITY))
        assertTrue(gate.confirmed)
    }
}
