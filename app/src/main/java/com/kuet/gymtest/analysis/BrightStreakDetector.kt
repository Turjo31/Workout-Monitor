package com.kuet.gymtest.analysis

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A cheap, conservative candidate finder; brightness alone never produces a warning. */
internal object BrightStreakDetector {
    data class Region(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        fun containsCenter(other: Region) = (other.left + other.right) / 2 in left..right &&
            (other.top + other.bottom) / 2 in top..bottom
        fun expanded(padding: Int, width: Int, height: Int) = Region(
            max(0, left - padding), max(0, top - padding),
            min(width, right + padding), min(height, bottom + padding)
        )
    }

    /** Pixels are the requested crop, while returned coordinates are in the full bitmap. */
    fun find(pixels: IntArray, width: Int, height: Int, originX: Int, originY: Int,
             fullWidth: Int, fullHeight: Int): Region? {
        val step = 4
        val columns = (width + step - 1) / step
        val rows = (height + step - 1) / step
        if (columns == 0 || rows == 0) return null
        val luminance = IntArray(columns * rows)
        val neutral = BooleanArray(columns * rows)
        for (gy in 0 until rows) for (gx in 0 until columns) {
            val color = pixels[min(height - 1, gy * step) * width + min(width - 1, gx * step)]
            val r = color shr 16 and 255
            val g = color shr 8 and 255
            val b = color and 255
            val index = gy * columns + gx
            luminance[index] = (r + g + b) / 3
            neutral[index] = max(r, max(g, b)) - min(r, min(g, b)) <= 45
        }
        val bright = BooleanArray(columns * rows)
        val sideOffsets = arrayOf(6 to 0, 0 to 6, 4 to 4, 4 to -4)
        for (gy in 0 until rows) for (gx in 0 until columns) {
            val index = gy * columns + gx
            val center = luminance[index]
            if (center < 170 || !neutral[index]) continue
            // A streak can be pale grey rather than clipped white. Require it to be
            // brighter than the image on both sides of at least one axis, not just bright.
            for ((dx, dy) in sideOffsets) {
                val ax = gx - dx; val ay = gy - dy
                val bx = gx + dx; val by = gy + dy
                if (ax !in 0 until columns || bx !in 0 until columns ||
                    ay !in 0 until rows || by !in 0 until rows) continue
                if (center - luminance[ay * columns + ax] >= 15 &&
                    center - luminance[by * columns + bx] >= 15) {
                    bright[index] = true
                    break
                }
            }
        }
        val visited = BooleanArray(bright.size)
        val queue = IntArray(bright.size)
        var best: Region? = null
        var bestLength = 0.0
        for (start in bright.indices) {
            if (!bright[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var count = 0
            var minX = columns; var maxX = 0; var minY = rows; var maxY = 0
            var sumX = 0.0; var sumY = 0.0; var sumXX = 0.0; var sumYY = 0.0; var sumXY = 0.0
            while (head < tail) {
                val index = queue[head++]
                val x = index % columns
                val y = index / columns
                count++
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
                sumX += x; sumY += y; sumXX += x * x; sumYY += y * y; sumXY += x * y
                for (ny in max(0, y - 1)..min(rows - 1, y + 1))
                    for (nx in max(0, x - 1)..min(columns - 1, x + 1)) {
                        val next = ny * columns + nx
                        if (bright[next] && !visited[next]) {
                            visited[next] = true
                            queue[tail++] = next
                        }
                    }
            }
            val fullSamples = ((fullWidth + step - 1) / step) * ((fullHeight + step - 1) / step)
            if (count < 12 || count > fullSamples / 10) continue
            val xx = sumXX / count - (sumX / count) * (sumX / count)
            val yy = sumYY / count - (sumY / count) * (sumY / count)
            val xy = sumXY / count - (sumX / count) * (sumY / count)
            val root = sqrt(max(0.0, (xx - yy) * (xx - yy) + 4 * xy * xy))
            val major = (xx + yy + root) / 2
            val minor = max(0.0, (xx + yy - root) / 2)
            val length = max(maxX - minX, maxY - minY) * step.toDouble()
            if (length < min(fullWidth, fullHeight) * .18 || major < 16 * (minor + 1)) continue
            if (length > bestLength) {
                bestLength = length
                best = Region(originX + minX * step, originY + minY * step,
                    min(fullWidth, originX + (maxX + 1) * step),
                    min(fullHeight, originY + (maxY + 1) * step))
            }
        }
        return best
    }
}
