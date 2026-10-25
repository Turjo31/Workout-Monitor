package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

data class Point2(val x: Double, val y: Double)

object PoseGeometry {
    fun point(landmark: PoseLandmark, width: Int, height: Int): Point2? {
        if (width <= 0 || height <= 0 || !landmark.x.isFinite() ||
            !landmark.y.isFinite() || !landmark.z.isFinite()) return null
        return Point2(landmark.x.toDouble() * width, landmark.y.toDouble() * height)
    }

    fun distance(a: Point2, b: Point2): Double = hypot(a.x - b.x, a.y - b.y)

    fun angle(a: Point2, b: Point2, c: Point2, minLength: Double = 1e-6): Double? {
        val ux = a.x - b.x
        val uy = a.y - b.y
        val vx = c.x - b.x
        val vy = c.y - b.y
        if (listOf(ux, uy, vx, vy).any { !it.isFinite() } ||
            hypot(ux, uy) < minLength || hypot(vx, vy) < minLength) return null
        return Math.toDegrees(atan2(abs(ux * vy - uy * vx), ux * vx + uy * vy)).takeIf { it.isFinite() }
    }

    fun confident(l: PoseLandmark?, visibility: Float = .65f, presence: Float = .5f): Boolean =
        l != null && l.x.isFinite() && l.y.isFinite() && l.z.isFinite() &&
            l.visibility?.let { it.isFinite() && it in visibility..1f } == true &&
            (l.presence == null || l.presence.isFinite() && l.presence in presence..1f)

    fun inside(l: PoseLandmark, margin: Float = .02f) = l.x in margin..(1 - margin) && l.y in margin..(1 - margin)
}
