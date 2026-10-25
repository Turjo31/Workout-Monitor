package com.kuet.gymtest

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Size
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class CameraFrameConversionTest {
    @Test fun paddedRgbaCropAndAllRotationsMatchInverseMapping() {
        val bytes = ByteBuffer.allocate(5 + 24 * 4)
        bytes.position(5)
        for (y in 0..3) for (x in 0..3) {
            val offset = 5 + y * 24 + x * 4
            bytes.put(offset, (x * 40).toByte()); bytes.put(offset + 1, (y * 50).toByte())
            bytes.put(offset + 2, 20); bytes.put(offset + 3, 255.toByte())
        }
        for (rotation in listOf(0, 90, 180, 270)) {
            val converted = CameraFrameConversion.convert(bytes, 24, 4, Rect(1, 1, 4, 3), rotation)
            val bitmap = converted.bitmap
            assertEquals(if (rotation % 180 == 0) 3 else 2, bitmap.width)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val point = floatArrayOf(x + .5f, y + .5f)
                converted.modelToRaw.mapPoints(point)
                val expected = Color.argb(255, point[0].toInt() * 40, point[1].toInt() * 50, 20)
                assertEquals("rotation=$rotation x=$x y=$y", expected, bitmap.getPixel(x, y))
            }
            bitmap.recycle()
        }
        assertEquals(5, bytes.position())
    }

    @androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
    @Test fun displayReflectionDoesNotChangeAnalysisCoordinates() {
        val source = OutputTransform(Matrix(), Size(100, 200))
        val rear = OutputTransform(Matrix(), Size(100, 200))
        val frontMatrix = Matrix().apply { setScale(-1f, 1f); postTranslate(100f, 0f) }
        val front = OutputTransform(frontMatrix, Size(100, 200))
        val original = floatArrayOf(20f, 30f)
        val rearPoint = original.copyOf(); val frontPoint = original.copyOf()
        CoordinateTransform(source, rear).mapPoints(rearPoint)
        CoordinateTransform(source, front).mapPoints(frontPoint)
        assertArrayEquals(floatArrayOf(20f, 30f), rearPoint, .001f)
        assertArrayEquals(floatArrayOf(80f, 30f), frontPoint, .001f)
        assertArrayEquals(floatArrayOf(20f, 30f), original, .001f)
    }
}
