package com.kuet.gymtest

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import java.nio.ByteBuffer

/** CameraX RGBA crop -> upright, unmirrored pixels; inverse maps model pixels to raw buffer. */
object CameraFrameConversion {
    data class Converted(val bitmap: Bitmap, val modelToRaw: Matrix)
    fun convert(buffer: ByteBuffer, rowStride: Int, pixelStride: Int, crop: Rect, rotation: Int): Converted {
        require(crop.width() > 0 && crop.height() > 0 && crop.left >= 0 && crop.top >= 0)
        require(rotation in listOf(0, 90, 180, 270) && pixelStride >= 4 && rowStride > 0)
        val copy = buffer.duplicate()
        val base = copy.position()
        val pixels = IntArray(crop.width() * crop.height())
        for (y in 0 until crop.height()) for (x in 0 until crop.width()) {
            val index = base + (y + crop.top) * rowStride + (x + crop.left) * pixelStride
            val r = copy.get(index).toInt() and 255
            val g = copy.get(index + 1).toInt() and 255
            val b = copy.get(index + 2).toInt() and 255
            val a = copy.get(index + 3).toInt() and 255
            pixels[y * crop.width() + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val cropped = Bitmap.createBitmap(pixels, crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
        try {
            val rotate = Matrix().apply { postRotate(rotation.toFloat()) }
            val bounds = RectF(0f, 0f, crop.width().toFloat(), crop.height().toFloat())
            rotate.mapRect(bounds)
            val rawToModel = Matrix().apply {
                postTranslate(-crop.left.toFloat(), -crop.top.toFloat())
                postRotate(rotation.toFloat())
                postTranslate(-bounds.left, -bounds.top)
            }
            val inverse = Matrix()
            check(rawToModel.invert(inverse))
            val model = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, rotate, false)
            if (model !== cropped) cropped.recycle()
            return Converted(model, inverse)
        } catch (error: Exception) { cropped.recycle(); throw error }
    }
}
