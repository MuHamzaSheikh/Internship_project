package com.example.businesscardscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object EnhancementPipeline {
    private const val TAG = "EnhancementPipeline"

    fun process(context: Context, sourceUri: Uri, filterMode: String, rotationDegrees: Float = 0f): Uri? {
        var bitmap = ImagePreprocessor.loadSafeBitmap(context, sourceUri, 4000)
        
        if (rotationDegrees != 0f) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val resultBitmap = applyMode(bitmap, filterMode)

        val file = java.io.File(context.cacheDir, "filtered_${System.currentTimeMillis()}.jpg")
        return try {
            java.io.FileOutputStream(file).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save filtered image", e)
            null
        }
    }

    private fun applyMode(bitmap: Bitmap, mode: String): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val resultMat = Mat()

        when (mode) {
            "Color" -> applyColor(mat, resultMat)
            "Magic Color" -> applyMagicColor(mat, resultMat)
            "Black & White" -> applyBlackAndWhite(mat, resultMat)
            "Grayscale" -> applyGrayscale(mat, resultMat)
            "High Contrast" -> applyHighContrast(mat, resultMat)
            "Original" -> mat.copyTo(resultMat)
            else -> mat.copyTo(resultMat) // Fallback
        }

        // Ensure 4 channels before converting back to Bitmap
        if (resultMat.channels() == 3) {
            Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_RGB2RGBA)
        } else if (resultMat.channels() == 1) {
            Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_GRAY2RGBA)
        }

        val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resultMat, resultBitmap)

        mat.release()
        resultMat.release()
        return resultBitmap
    }

    private fun applyColor(src: Mat, dst: Mat) {
        val hsvMat = Mat()
        if (src.channels() == 4) {
            Imgproc.cvtColor(src, hsvMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsvMat, hsvMat, Imgproc.COLOR_RGB2HSV)
        } else {
            Imgproc.cvtColor(src, hsvMat, Imgproc.COLOR_RGB2HSV)
        }
        val channels = java.util.ArrayList<Mat>()
        Core.split(hsvMat, channels)
        
        // Boost saturation by 30%
        Core.multiply(channels[1], org.opencv.core.Scalar(1.3), channels[1])
        
        Core.merge(channels, hsvMat)
        Imgproc.cvtColor(hsvMat, dst, Imgproc.COLOR_HSV2RGB)
        
        hsvMat.release()
        channels.forEach { it.release() }
    }

    private fun applyMagicColor(src: Mat, dst: Mat) {
        // Redesigned 10-step production enhancement pipeline
        
        val bgrMat = Mat()
        if (src.channels() == 4) {
            Imgproc.cvtColor(src, bgrMat, Imgproc.COLOR_RGBA2BGR)
        } else {
            src.copyTo(bgrMat)
        }

        // 1. Noise Reduction (Bilateral Filter)
        val denoised = Mat()
        Imgproc.bilateralFilter(bgrMat, denoised, 9, 75.0, 75.0)

        // 2. White Balance (Gray World Assumption)
        val balanced = Mat()
        applyWhiteBalance(denoised, balanced)
        denoised.release()

        // 3. Illumination Correction (Gamma Correction)
        val gamma = 1.2
        val lookUpTable = Mat(1, 256, CvType.CV_8U)
        val lutData = ByteArray(256)
        for (i in 0..255) {
            lutData[i] = (Math.pow(i / 255.0, gamma) * 255.0).toInt().toByte()
        }
        lookUpTable.put(0, 0, lutData)
        val illuminationCorrected = Mat()
        Core.LUT(balanced, lookUpTable, illuminationCorrected)
        balanced.release()
        lookUpTable.release()

        // 4. CLAHE (Contrast Limited Adaptive Histogram Equalization)
        val labMat = Mat()
        Imgproc.cvtColor(illuminationCorrected, labMat, Imgproc.COLOR_BGR2Lab)
        val labChannels = java.util.ArrayList<Mat>()
        Core.split(labMat, labChannels)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(labChannels[0], labChannels[0])
        Core.merge(labChannels, labMat)
        val claheCorrected = Mat()
        Imgproc.cvtColor(labMat, claheCorrected, Imgproc.COLOR_Lab2BGR)
        illuminationCorrected.release()
        labMat.release()
        labChannels.forEach { it.release() }
        clahe.collectGarbage()

        // 5. Contrast Stretching (Normalization)
        val stretched = Mat()
        Core.normalize(claheCorrected, stretched, 0.0, 255.0, Core.NORM_MINMAX)
        claheCorrected.release()

        // 6. Shadow Reduction (Morphological Closing)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
        val closed = Mat()
        Imgproc.morphologyEx(stretched, closed, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()
        
        // 7. Sharpen (Unsharp Masking)
        val blurred = Mat()
        Imgproc.GaussianBlur(closed, blurred, Size(0.0, 0.0), 3.0)
        val sharpened = Mat()
        Core.addWeighted(closed, 1.5, blurred, -0.5, 0.0, sharpened)
        closed.release()
        blurred.release()

        // 8. Adaptive Threshold (Use as a mask for contrast)
        val grayForThresh = Mat()
        Imgproc.cvtColor(sharpened, grayForThresh, Imgproc.COLOR_BGR2GRAY)
        val thresh = Mat()
        Imgproc.adaptiveThreshold(grayForThresh, thresh, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0)
        grayForThresh.release()

        // 9. Color Restoration (Saturation Boost)
        val hsvMat = Mat()
        Imgproc.cvtColor(sharpened, hsvMat, Imgproc.COLOR_BGR2HSV)
        val hsvChannels = java.util.ArrayList<Mat>()
        Core.split(hsvMat, hsvChannels)
        Core.multiply(hsvChannels[1], org.opencv.core.Scalar(1.2), hsvChannels[1])
        Core.merge(hsvChannels, hsvMat)
        val colorRestored = Mat()
        Imgproc.cvtColor(hsvMat, colorRestored, Imgproc.COLOR_HSV2BGR)
        hsvMat.release()
        hsvChannels.forEach { it.release() }
        sharpened.release()

        // Blend threshold with color
        val finalMixed = Mat()
        val threshBgr = Mat()
        Imgproc.cvtColor(thresh, threshBgr, Imgproc.COLOR_GRAY2BGR)
        Core.addWeighted(colorRestored, 0.7, threshBgr, 0.3, 0.0, finalMixed)
        thresh.release()
        threshBgr.release()
        colorRestored.release()

        // 10. Final Cleanup (Median Blur)
        Imgproc.medianBlur(finalMixed, dst, 3)
        finalMixed.release()
        bgrMat.release()
    }

    private fun removeShadows(src: Mat, dst: Mat) {
        val rgb = Mat()
        if (src.channels() == 4) {
            Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB)
        } else {
            src.copyTo(rgb)
        }

        val planes = java.util.ArrayList<Mat>()
        Core.split(rgb, planes)

        val resultPlanes = java.util.ArrayList<Mat>()
        for (plane in planes) {
            val dilated = Mat()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(21.0, 21.0))
            Imgproc.dilate(plane, dilated, kernel)
            
            val blurred = Mat()
            Imgproc.medianBlur(dilated, blurred, 21)
            
            val diff = Mat()
            Core.absdiff(plane, blurred, diff)
            
            val normalized = Mat()
            Core.bitwise_not(diff, diff)
            Core.normalize(diff, normalized, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8UC1)
            resultPlanes.add(normalized)
            
            dilated.release()
            kernel.release()
            blurred.release()
            diff.release()
        }

        Core.merge(resultPlanes, dst)

        rgb.release()
        planes.forEach { it.release() }
        resultPlanes.forEach { it.release() }
    }

    private fun applyWhiteBalance(src: Mat, dst: Mat) {
        // Simple Gray World assumption for white balance
        val rgb = Mat()
        if (src.channels() == 4) {
            Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB)
        } else {
            src.copyTo(rgb)
        }

        val channels = java.util.ArrayList<Mat>()
        Core.split(rgb, channels)

        val meanR = Core.mean(channels[0]).`val`[0]
        val meanG = Core.mean(channels[1]).`val`[0]
        val meanB = Core.mean(channels[2]).`val`[0]

        val meanGray = (meanR + meanG + meanB) / 3.0

        Core.multiply(channels[0], org.opencv.core.Scalar(meanGray / meanR), channels[0])
        Core.multiply(channels[1], org.opencv.core.Scalar(meanGray / meanG), channels[1])
        Core.multiply(channels[2], org.opencv.core.Scalar(meanGray / meanB), channels[2])

        Core.merge(channels, dst)

        rgb.release()
        channels.forEach { it.release() }
    }

    private fun applyBlackAndWhite(src: Mat, dst: Mat) {
        val gray = Mat()
        if (src.channels() >= 3) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        } else {
            src.copyTo(gray)
        }
        
        // Remove shadows first to get a clean background
        val shadowRemoved = Mat()
        removeShadows(src, shadowRemoved)
        val shadowGray = Mat()
        Imgproc.cvtColor(shadowRemoved, shadowGray, Imgproc.COLOR_RGB2GRAY)
        
        // Adaptive thresholding for clean black and white text
        Imgproc.adaptiveThreshold(
            shadowGray,
            dst,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            21,
            10.0
        )
        
        gray.release()
        shadowRemoved.release()
        shadowGray.release()
    }

    private fun applyGrayscale(src: Mat, dst: Mat) {
        if (src.channels() >= 3) {
            Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGBA2GRAY)
        } else {
            src.copyTo(dst)
        }
    }

    private fun applyHighContrast(src: Mat, dst: Mat) {
        val rgb = Mat()
        if (src.channels() == 4) {
            Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB)
        } else {
            src.copyTo(rgb)
        }
        
        // Gamma correction and contrast stretch
        rgb.convertTo(dst, -1, 1.5, 20.0)
        
        rgb.release()
    }
}
