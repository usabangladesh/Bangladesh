package com.rashed.ai.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Throttles and converts CameraX ImageProxy frames into optimized JPEG chunks for Gemini Live.
 * Target: ~1 frame/second, scaled to prevent bandwidth saturation.
 */
class CameraFrameProcessor(
    private val frameIntervalMs: Long = 1000L,
    private val maxDimension: Int = 640,
    private val jpegQuality: Int = 75,
    private val onFrameProcessed: (jpegBase64: String) -> Unit
) {

    companion object {
        private const val TAG = "CameraFrameProcessor"
    }

    private var lastProcessedTimestamp = 0L

    fun processImage(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTimestamp < frameIntervalMs) {
            imageProxy.close()
            return
        }

        lastProcessedTimestamp = currentTime

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                val rotatedAndScaled = transformBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
                val base64String = bitmapToBase64(rotatedAndScaled)
                onFrameProcessed(base64String)
                if (rotatedAndScaled != bitmap) {
                    rotatedAndScaled.recycle()
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image frame: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val planes = imageProxy.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)

        val uPixelStride = planes[1].pixelStride
        val vPixelStride = planes[2].pixelStride

        if (uPixelStride == 2 && vPixelStride == 2) {
            // Already interleaved (NV21 / NV12)
            vBuffer.get(nv21, ySize, vSize)
        } else {
            // Interleave U and V
            val uvWidth = imageProxy.width / 2
            val uvHeight = imageProxy.height / 2
            var offset = ySize
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val vIndex = row * planes[2].rowStride + col * vPixelStride
                    val uIndex = row * planes[1].rowStride + col * uPixelStride
                    if (vIndex < vBuffer.limit() && uIndex < uBuffer.limit()) {
                        nv21[offset++] = vBuffer.get(vIndex)
                        nv21[offset++] = uBuffer.get(uIndex)
                    }
                }
            }
        }

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), jpegQuality, out)
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun transformBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }

        val width = bitmap.width
        val height = bitmap.height
        val maxSide = maxOf(width, height)

        if (maxSide > maxDimension) {
            val scale = maxDimension.toFloat() / maxSide
            matrix.postScale(scale, scale)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
