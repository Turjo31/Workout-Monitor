package com.kuet.gymtest.analysis

import org.junit.Assert.*
import org.junit.Test

class FrameClockTest {
    @Test fun anchoredIntervalsAndDuplicates() {
        val c = FrameClock()
        assertEquals(1000L, c.timestamp(9_000_000_000, 1000))
        assertEquals(1033L, c.timestamp(9_033_000_000, 1035))
        assertNull(c.timestamp(9_033_000_000, 1040))
    }
    @Test(expected = IllegalArgumentException::class) fun rollbackFailsClosed() {
        val c = FrameClock(); c.timestamp(100, 0); c.timestamp(90, 1)
    }
    @Test(expected = IllegalArgumentException::class) fun clockJumpFailsClosed() {
        val c = FrameClock(); c.timestamp(1_000_000, 100); c.timestamp(5_000_000_000, 110)
    }
}
