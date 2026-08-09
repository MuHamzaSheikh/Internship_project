package com.example.businesscardscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

object ImagePreprocessor {
    private const val PERF_TAG = "CardScannerPerf"
    private const val TAG = "CardScannerOcr"
    private const val OCR_MAX_DIMENSION = 3000

    init {
        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV")
        } else {
            Log.d(TAG, "OpenCV loaded successfully")
        }
    }

    enum class ImageQuality {
        GOOD, TOO_BLURRY, MOTION_BLUR, TOO_DARK, TOO_BRIGHT, GLARE
    }

    fun measureImageQuality(bitmap: Bitmap): ImageQuality {
        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)

        val grayMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, grayMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
        
        val laplacian = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.Laplacian(grayMat, laplacian, org.opencv.core.CvType.CV_64F)
        val mean = org.opencv.core.MatOfDouble()
        val stddev = org.opencv.core.MatOfDouble()
        org.opencv.core.Core.meanStdDev(laplacian, mean, stddev)
        val variance = Math.pow(stddev.get(0, 0)[0], 2.0)
        
        val hsvMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, hsvMat, org.opencv.imgproc.Imgproc.COLOR_RGB2HSV)
        val channels = java.util.ArrayList<org.opencv.core.Mat>()
        org.opencv.core.Core.split(hsvMat, channels)
        val vMean = org.opencv.core.Core.mean(channels[2]).`val`[0]

        val sobelX = org.opencv.core.Mat()
        val sobelY = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.Sobel(grayMat, sobelX, org.opencv.core.CvType.CV_64F, 1, 0)
        org.opencv.imgproc.Imgproc.Sobel(grayMat, sobelY, org.opencv.core.CvType.CV_64F, 0, 1)
        val meanX = org.opencv.core.MatOfDouble()
        val stddevX = org.opencv.core.MatOfDouble()
        org.opencv.core.Core.meanStdDev(sobelX, meanX, stddevX)
        val varX = Math.pow(stddevX.get(0, 0)[0], 2.0)
        val meanY = org.opencv.core.MatOfDouble()
        val stddevY = org.opencv.core.MatOfDouble()
        org.opencv.core.Core.meanStdDev(sobelY, meanY, stddevY)
        val varY = Math.pow(stddevY.get(0, 0)[0], 2.0)

        // Glare Detection (Saturated white spots)
        val glareMask = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.threshold(channels[2], glareMask, 245.0, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
        val glarePixels = org.opencv.core.Core.countNonZero(glareMask)
        val totalPixels = mat.cols() * mat.rows()
        val glareRatio = glarePixels.toDouble() / totalPixels
        val hasGlare = glareRatio > 0.01 // If more than 1% of the image is pure white glare

        mat.release()
        grayMat.release()
        laplacian.release()
        hsvMat.release()
        sobelX.release()
        sobelY.release()
        channels.forEach { it.release() }
        glareMask.release()

        Log.d(TAG, "Quality Check: variance=$variance, brightness=$vMean, glareRatio=$glareRatio")
        
        // Thresholds (these might need tuning, using conservative values for now)
        val ratio = if (varX > varY) varX / (varY + 1.0) else varY / (varX + 1.0)
        
        return when {
            variance < 50.0 && ratio > 2.0 -> ImageQuality.MOTION_BLUR
            variance < 50.0 -> ImageQuality.TOO_BLURRY
            hasGlare -> ImageQuality.GLARE
            vMean < 40.0 -> ImageQuality.TOO_DARK
            vMean > 240.0 -> ImageQuality.TOO_BRIGHT
            else -> ImageQuality.GOOD
        }
    }

    fun loadSafeBitmap(context: Context, uri: Uri, maxDimension: Int = OCR_MAX_DIMENSION): Bitmap {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }
        val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, maxDimension)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val original = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: error("Unable to decode image")

        val oriented = rotateIfNeeded(context, uri, original)
        return if (oriented.width > maxDimension || oriented.height > maxDimension) {
            scaleToMaxDimension(oriented, maxDimension)
        } else oriented
    }

    suspend fun loadAndEnhance(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        Log.d(PERF_TAG, "capture:orientation_correction_start")
        val normalized = loadSafeBitmap(context, uri, OCR_MAX_DIMENSION)

        val orientedCropped = if (normalized.height > normalized.width) {
            val matrix = Matrix().apply { postRotate(-90f) }
            Bitmap.createBitmap(normalized, 0, 0, normalized.width, normalized.height, matrix, true)
        } else {
            normalized
        }

        val enhanced = applyAdaptiveEnhancement(orientedCropped)
        Log.d(PERF_TAG, "capture:orientation_correction_end elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
        Log.d(TAG, "OCR input size=${enhanced.width}x${enhanced.height}")
        enhanced
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetMaxDimension: Int): Int {
        var inSampleSize = 1
        while ((width / inSampleSize) > targetMaxDimension || (height / inSampleSize) > targetMaxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, targetMaxDimension: Int): Bitmap {
        val scale = targetMaxDimension.toFloat() / bitmap.width.coerceAtLeast(bitmap.height).toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun rotateIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        } ?: ExifInterface.ORIENTATION_UNDEFINED

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * WARNING: REGRESSION SAFETY NET
     * This enhancement pipeline has broken silently multiple times. 
     * DO NOT MODIFY without running the QA_REGRESSION_PIPELINE.md checklist.
     */
    fun applyAdaptiveEnhancement(bitmap: Bitmap): Bitmap {
        Log.d("CardScannerEnhance", "PIPELINE INVOCATION: applyAdaptiveEnhancement started")
        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)

        // Step 1 - Convert to grayscale for measurement
        val grayMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, grayMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
        
        // Step 2 - Measure Blur (Laplacian Variance)
        val laplacian = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.Laplacian(grayMat, laplacian, org.opencv.core.CvType.CV_64F)
        val mean = org.opencv.core.MatOfDouble()
        val stddev = org.opencv.core.MatOfDouble()
        org.opencv.core.Core.meanStdDev(laplacian, mean, stddev)
        val variance = Math.pow(stddev.get(0, 0)[0], 2.0)
        laplacian.release()
        
        Log.d("CardScannerEnhance", "Laplacian variance = $variance")

        // Step 3 - Measure Brightness (Mean Luminance)
        // Convert to HSV to get V channel
        val hsvMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, hsvMat, org.opencv.imgproc.Imgproc.COLOR_RGB2HSV)
        val channels = java.util.ArrayList<org.opencv.core.Mat>()
        org.opencv.core.Core.split(hsvMat, channels)
        
        val vChannel = channels[2]
        val vMean = org.opencv.core.Core.mean(vChannel).`val`[0]
        Log.d("CardScannerEnhance", "Average Brightness (V) = $vMean")

        var currentMat = mat.clone()

        // Step 4 - Proportional Sharpening
        if (variance < 120.0) {
            val blurFactor = (120.0 - variance) / 120.0
            val alpha = 1.0 + (1.5 * blurFactor)
            val beta = 1.0 - alpha
            Log.d("CardScannerEnhance", "Applying proportional sharpening with alpha=$alpha, beta=$beta")
            
            val blurred = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.GaussianBlur(currentMat, blurred, org.opencv.core.Size(0.0, 0.0), 3.0)
            org.opencv.core.Core.addWeighted(currentMat, alpha, blurred, beta, 0.0, currentMat)
            blurred.release()
        } else {
            Log.d("CardScannerEnhance", "Image is sharp enough, skipping sharpening")
        }

        // Step 5 - Proportional Brightness Adjustment
        if (vMean < 90.0) {
            val darkFactor = (90.0 - vMean) / 90.0
            val clipLimit = 1.0 + (3.0 * darkFactor)
            Log.d("CardScannerEnhance", "Applying proportional CLAHE with clipLimit=$clipLimit")
            val clahe = org.opencv.imgproc.Imgproc.createCLAHE(clipLimit, org.opencv.core.Size(8.0, 8.0))
            
            val currentHsv = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.cvtColor(currentMat, currentHsv, org.opencv.imgproc.Imgproc.COLOR_RGB2HSV)
            val currentChannels = java.util.ArrayList<org.opencv.core.Mat>()
            org.opencv.core.Core.split(currentHsv, currentChannels)
            
            clahe.apply(currentChannels[2], currentChannels[2])
            clahe.collectGarbage()
            
            org.opencv.core.Core.merge(currentChannels, currentHsv)
            org.opencv.imgproc.Imgproc.cvtColor(currentHsv, currentMat, org.opencv.imgproc.Imgproc.COLOR_HSV2RGB)
            currentHsv.release()
            currentChannels.forEach { it.release() }
        } else if (vMean > 180.0) {
            val brightFactor = (vMean - 180.0) / 75.0
            val gamma = 1.0 + (0.8 * brightFactor)
            Log.d("CardScannerEnhance", "Applying proportional gamma reduction with gamma=$gamma")
            
            val lookUpTable = org.opencv.core.Mat(1, 256, org.opencv.core.CvType.CV_8U)
            val lutData = ByteArray(256)
            for (i in 0..255) {
                lutData[i] = (Math.pow(i / 255.0, gamma) * 255.0).toInt().toByte()
            }
            lookUpTable.put(0, 0, lutData)
            org.opencv.core.Core.LUT(currentMat, lookUpTable, currentMat)
            lookUpTable.release()
        } else {
            Log.d("CardScannerEnhance", "Brightness is acceptable, skipping correction")
        }

        // Step 6 - Dullness / Low-Contrast Detection
        val stddevDull = org.opencv.core.MatOfDouble()
        val meanDull = org.opencv.core.MatOfDouble()
        org.opencv.core.Core.meanStdDev(grayMat, meanDull, stddevDull)
        val contrast = stddevDull.get(0, 0)[0]
        Log.d("CardScannerEnhance", "Contrast (StdDev) = $contrast")

        if (contrast < 40.0) {
            Log.d("CardScannerEnhance", "Image is dull/low-contrast. Applying histogram stretch and CLAHE.")
            val claheDull = org.opencv.imgproc.Imgproc.createCLAHE(4.0, org.opencv.core.Size(8.0, 8.0))
            val hsvDull = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.cvtColor(currentMat, hsvDull, org.opencv.imgproc.Imgproc.COLOR_RGB2HSV)
            val channelsDull = java.util.ArrayList<org.opencv.core.Mat>()
            org.opencv.core.Core.split(hsvDull, channelsDull)
            
            // Normalize V channel to full 0-255 range (stretch)
            org.opencv.core.Core.normalize(channelsDull[2], channelsDull[2], 0.0, 255.0, org.opencv.core.Core.NORM_MINMAX)
            // Apply aggressive CLAHE
            claheDull.apply(channelsDull[2], channelsDull[2])
            
            org.opencv.core.Core.merge(channelsDull, hsvDull)
            org.opencv.imgproc.Imgproc.cvtColor(hsvDull, currentMat, org.opencv.imgproc.Imgproc.COLOR_HSV2RGB)
            hsvDull.release()
            channelsDull.forEach { it.release() }
        }

        // Ensure alpha channel is added back if needed by converting to RGBA
        if (currentMat.channels() == 3) {
            org.opencv.imgproc.Imgproc.cvtColor(currentMat, currentMat, org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA)
        }

        val resultBitmap = Bitmap.createBitmap(currentMat.cols(), currentMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(currentMat, resultBitmap)
        
        mat.release()
        grayMat.release()
        hsvMat.release()
        channels.forEach { it.release() }
        currentMat.release()
        
        return resultBitmap
    }




    private fun enhance(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var rMin = 255
        var gMin = 255
        var bMin = 255
        var rMax = 0
        var gMax = 0
        var bMax = 0
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            rMin = rMin.coerceAtMost(r)
            gMin = gMin.coerceAtMost(g)
            bMin = bMin.coerceAtMost(b)
            rMax = rMax.coerceAtLeast(r)
            gMax = gMax.coerceAtLeast(g)
            bMax = bMax.coerceAtLeast(b)
        }

        val outputPixels = IntArray(pixels.size)
        for (index in pixels.indices) {
            val x = index % width
            val y = index / width
            val source = pixels[index]
            val alpha = Color.alpha(source)
            val centerR = Color.red(source)
            val centerG = Color.green(source)
            val centerB = Color.blue(source)

            val blurR = sampleBlurredChannel(pixels, width, height, x, y, centerR) { pixel -> Color.red(pixel) }
            val blurG = sampleBlurredChannel(pixels, width, height, x, y, centerG) { pixel -> Color.green(pixel) }
            val blurB = sampleBlurredChannel(pixels, width, height, x, y, centerB) { pixel -> Color.blue(pixel) }

            val sharpenedR = clampColor(centerR + (centerR - blurR) * 0.45f)
            val sharpenedG = clampColor(centerG + (centerG - blurG) * 0.45f)
            val sharpenedB = clampColor(centerB + (centerB - blurB) * 0.45f)

            val stretchedR = applyContrastStretch(sharpenedR, rMin, rMax)
            val stretchedG = applyContrastStretch(sharpenedG, gMin, gMax)
            val stretchedB = applyContrastStretch(sharpenedB, bMin, bMax)

            outputPixels[index] = Color.argb(alpha, stretchedR, stretchedG, stretchedB)
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(outputPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun sampleBlurredChannel(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        centerValue: Int,
        extractor: (Int) -> Int
    ): Int {
        var sum = centerValue * 4
        var count = 4
        val left = x - 1
        val right = x + 1
        val top = y - 1
        val bottom = y + 1
        if (left >= 0) {
            sum += extractor(pixels[y * width + left])
            count++
        }
        if (right < width) {
            sum += extractor(pixels[y * width + right])
            count++
        }
        if (top >= 0) {
            sum += extractor(pixels[top * width + x])
            count++
        }
        if (bottom < height) {
            sum += extractor(pixels[bottom * width + x])
            count++
        }
        return (sum / count.toFloat()).roundToInt()
    }

    internal fun applyContrastStretch(value: Int, low: Int, high: Int): Int {
        if (high <= low) return value.coerceIn(0, 255)
        val scaled = ((value - low).toFloat() / (high - low).toFloat()) * 255f
        return scaled.roundToInt().coerceIn(0, 255)
    }

    private fun clampColor(value: Float): Int = value.roundToInt().coerceIn(0, 255)

    fun applyManualFilter(context: Context, sourceUri: Uri, filterType: String, rotationDegrees: Float = 0f): Uri? {
        var original = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null

        if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            original = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        }

        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(original, mat)
        val resultMat = org.opencv.core.Mat()

        when (filterType) {
            "Sharpen" -> {
                val blur = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.GaussianBlur(mat, blur, org.opencv.core.Size(0.0, 0.0), 3.0)
                org.opencv.core.Core.addWeighted(mat, 1.5, blur, -0.5, 0.0, resultMat)
                blur.release()
            }
            "BW" -> {
                org.opencv.imgproc.Imgproc.cvtColor(mat, resultMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
                org.opencv.imgproc.Imgproc.cvtColor(resultMat, resultMat, org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA)
            }
            "BW_Scan" -> {
                val gray = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.cvtColor(mat, gray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
                // Mild blur to reduce noise before thresholding
                org.opencv.imgproc.Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(5.0, 5.0), 0.0)
                org.opencv.imgproc.Imgproc.adaptiveThreshold(
                    gray, resultMat, 255.0,
                    org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    org.opencv.imgproc.Imgproc.THRESH_BINARY, 21, 10.0
                )
                org.opencv.imgproc.Imgproc.cvtColor(resultMat, resultMat, org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA)
                gray.release()
            }
            "Shadow_Remove" -> {
                val rgb = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.cvtColor(mat, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
                
                val bg = org.opencv.core.Mat()
                // Dilation estimates the background by expanding the bright areas over text
                val kernel = org.opencv.imgproc.Imgproc.getStructuringElement(org.opencv.imgproc.Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(21.0, 21.0))
                org.opencv.imgproc.Imgproc.dilate(rgb, bg, kernel)
                org.opencv.imgproc.Imgproc.GaussianBlur(bg, bg, org.opencv.core.Size(21.0, 21.0), 0.0)
                
                // subtract/divide approach: dst = 255 - (255 - src) * 255 / (255 - bg) roughly, or just src/bg * 255
                // A simpler normalized division:
                val diff = org.opencv.core.Mat()
                org.opencv.core.Core.divide(rgb, bg, diff, 255.0) // diff = (rgb / bg) * 255
                
                org.opencv.imgproc.Imgproc.cvtColor(diff, resultMat, org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA)
                
                rgb.release()
                bg.release()
                kernel.release()
                diff.release()
            }
            "Magic_Color" -> {
                // Convert to HSV, boost Value of near-white background to 255, preserve color
                val hsv = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.cvtColor(mat, hsv, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
                org.opencv.imgproc.Imgproc.cvtColor(hsv, hsv, org.opencv.imgproc.Imgproc.COLOR_RGB2HSV)
                
                val channels = java.util.ArrayList<org.opencv.core.Mat>()
                org.opencv.core.Core.split(hsv, channels)
                
                // Create a mask for near-white/light-gray (low saturation, high value)
                val sChannel = channels[1]
                val vChannel = channels[2]
                
                val sMask = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.threshold(sChannel, sMask, 80.0, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY_INV)
                
                val vMask = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.threshold(vChannel, vMask, 180.0, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
                
                val bgMask = org.opencv.core.Mat()
                org.opencv.core.Core.bitwise_and(sMask, vMask, bgMask)
                
                // Boost Value channel in background areas to 255
                vChannel.setTo(org.opencv.core.Scalar(255.0), bgMask)
                
                org.opencv.core.Core.merge(channels, hsv)
                org.opencv.imgproc.Imgproc.cvtColor(hsv, resultMat, org.opencv.imgproc.Imgproc.COLOR_HSV2RGB)
                org.opencv.imgproc.Imgproc.cvtColor(resultMat, resultMat, org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA)
                
                hsv.release()
                channels.forEach { it.release() }
                sMask.release()
                vMask.release()
                bgMask.release()
            }
            "Contrast" -> {
                mat.convertTo(resultMat, -1, 1.5, 20.0)
            }
            "Enhance" -> {
                val enhancedBitmap = applyAdaptiveEnhancement(original)
                org.opencv.android.Utils.bitmapToMat(enhancedBitmap, resultMat)
            }
            else -> {
                mat.copyTo(resultMat)
            }
        }

        val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(resultMat, resultBitmap)

        mat.release()
        resultMat.release()

        val file = java.io.File(context.cacheDir, "filtered_${System.currentTimeMillis()}.jpg")
        try {
            java.io.FileOutputStream(file).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            return Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save filtered image", e)
        }
        return null
    }
}
