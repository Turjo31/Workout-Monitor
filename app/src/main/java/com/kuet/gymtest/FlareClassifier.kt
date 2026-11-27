package com.kuet.gymtest

import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/** Runs the bundled full-frame classifier on the dedicated flare worker, without JPEG encoding. */
class FlareClassifier(context: Context) : AutoCloseable {
    private val width = 160
    private val height = 160
    private val model = context.assets.open("flare_classifier.tflite").use { stream ->
        val bytes = stream.readBytes()
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }
    }
    private val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
    private val input = ByteBuffer.allocateDirect(width * height * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(width * height)
    private val output = Array(1) { FloatArray(1) }

    init {
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, height, width, 3)))
        require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
        require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 1)))
    }

    fun score(bitmap: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            input.rewind()
            for (pixel in pixels) {
                input.putFloat(((pixel ushr 16) and 255).toFloat())
                input.putFloat(((pixel ushr 8) and 255).toFloat())
                input.putFloat((pixel and 255).toFloat())
            }
            input.rewind()
            interpreter.run(input, output)
            return output[0][0]
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    override fun close() = interpreter.close()
}
