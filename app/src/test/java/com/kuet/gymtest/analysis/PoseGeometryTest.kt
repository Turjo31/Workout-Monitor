package com.kuet.gymtest.analysis

import org.junit.Assert.*
import org.junit.Test

class PoseGeometryTest {
    @Test fun anglesAndInvalidGeometry() {
        val b = Point2(0.0, 0.0)
        assertEquals(90.0, PoseGeometry.angle(Point2(1.0, 0.0), b, Point2(0.0, 1.0))!!, .0001)
        assertEquals(180.0, PoseGeometry.angle(Point2(-1.0, 0.0), b, Point2(1.0, 0.0))!!, .0001)
        assertEquals(0.0, PoseGeometry.angle(Point2(1.0, 0.0), b, Point2(2.0, 0.0))!!, .0001)
        assertNull(PoseGeometry.angle(b, b, Point2(1.0, 0.0)))
        assertNull(PoseGeometry.angle(Point2(Double.NaN, 0.0), b, Point2(1.0, 0.0)))
        assertNull(PoseGeometry.point(PoseLandmark(0f, 0f, 0f, 1f), 0, 10))
    }

    @Test fun geometryUsesPixelsAndIsReflectionInvariant() {
        fun p(x: Float, y: Float) = PoseGeometry.point(PoseLandmark(x, y, 0f, 1f), 200, 100)!!
        assertEquals(45.0, PoseGeometry.angle(p(1f, 0f), p(.5f, 0f), p(1f, 1f))!!, .001)
        assertEquals(45.0, PoseGeometry.angle(p(0f, 0f), p(.5f, 0f), p(0f, 1f))!!, .001)
    }

    @Test fun absentAndInvalidConfidenceIsNotInvented() {
        assertFalse(PoseGeometry.confident(PoseLandmark(.5f, .5f, 0f, null)))
        assertFalse(PoseGeometry.confident(PoseLandmark(.5f, .5f, 0f, 1f, Float.NaN)))
        assertTrue(PoseGeometry.confident(PoseLandmark(.5f, .5f, 0f, 1f)))
    }
}
