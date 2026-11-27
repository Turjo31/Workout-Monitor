package com.kuet.gymtest.analysis

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrightStreakDetectorTest {
    private val width = 160
    private val height = 160

    @Test fun longNarrowWhiteStreakIsCandidate() {
        val pixels = IntArray(width * height) { 0xff303030.toInt() }
        for (y in 12 until 144) for (x in 68 until 76) pixels[y * width + x] = -1
        val region = BrightStreakDetector.find(pixels, width, height, 0, 0, width, height)
        assertNotNull(region)
    }

    @Test fun whiteBlockAndIsolatedHighlightsAreNotStreaks() {
        val pixels = IntArray(width * height) { 0xff303030.toInt() }
        for (y in 40 until 80) for (x in 40 until 80) pixels[y * width + x] = -1
        for (y in 100 until 120 step 8) pixels[y * width + 120] = -1
        assertNull(BrightStreakDetector.find(pixels, width, height, 0, 0, width, height))
    }

    @Test fun paleStreakAgainstBrighterBackgroundIsCandidate() {
        val pixels = IntArray(width * height) { 0xff989898.toInt() }
        for (y in 12 until 144) for (x in 68 until 76)
            pixels[y * width + x] = 0xffd8dcda.toInt()
        assertNotNull(BrightStreakDetector.find(pixels, width, height, 0, 0, width, height))
    }
}
