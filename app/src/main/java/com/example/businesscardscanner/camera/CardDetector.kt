package com.example.businesscardscanner.camera

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.businesscardscanner.ocr.DocumentScanner
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

object CardDetector {
    private const val TAG = "CardDetector"

    fun processImageProxy(image: ImageProxy, rotationDegrees: Int): List<Array<Point>> {
        if (image.format != ImageFormat.YUV_420_888) {
            Log.e(TAG, "Unsupported image format: ${image.format}")
            image.close()
            return emptyList()
        }

        val yuvMat = imageProxyToYuvMat(image)
        val rgbMat = Mat()
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGBA_NV21)
        yuvMat.release()

        // Handle rotation from CameraX sensor
        val rotatedMat = if (rotationDegrees != 0) {
            val rotated = Mat()
            val angle = when (rotationDegrees) {
                90 -> Core.ROTATE_90_CLOCKWISE
                180 -> Core.ROTATE_180
                270 -> Core.ROTATE_90_COUNTERCLOCKWISE
                else -> -1
            }
            if (angle != -1) {
                Core.rotate(rgbMat, rotated, angle)
                rgbMat.release()
                rotated
            } else {
                rgbMat
            }
        } else {
            rgbMat
        }

        // Downscale for real-time performance (aim for max dimension of 640)
        val maxDimension = 640.0
        val width = rotatedMat.width().toDouble()
        val height = rotatedMat.height().toDouble()
        val scale = if (width > height) maxDimension / width else maxDimension / height
        
        val scaledMat = Mat()
        if (scale < 1.0) {
            Imgproc.resize(rotatedMat, scaledMat, Size(width * scale, height * scale))
        } else {
            rotatedMat.copyTo(scaledMat)
        }

        // Use our robust contour detector
        val detectedCornersList = DocumentScanner.detectMultipleCardsFromMat(scaledMat)
        scaledMat.release()
        rotatedMat.release()
        
        // Scale the points back up to the original rotated image coordinates
        val inverseScale = if (scale < 1.0) 1.0 / scale else 1.0
        
        return detectedCornersList.map { corners ->
            corners.map { point ->
                Point(point.x * inverseScale, point.y * inverseScale)
            }.toTypedArray()
        }
    }

    private fun imageProxyToYuvMat(image: ImageProxy): Mat {
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer

        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yPlane.get(nv21, 0, ySize)
        
        // NV21 puts V before U
        vPlane.get(nv21, ySize, vSize)
        uPlane.get(nv21, ySize + vSize, uSize)

        val yuvMat = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)
        return yuvMat
    }
}
