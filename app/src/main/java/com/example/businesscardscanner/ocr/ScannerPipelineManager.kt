package com.example.businesscardscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import com.example.businesscardscanner.ocr.ImagePreprocessor.ImageQuality
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ScannerPipelineManager(private val context: Context) {
    private val TAG = "ScannerPipelineManager"

    data class PipelineResult(
        val croppedUri: Uri?,
        val qualityWarning: String?,
        val ocrResult: OcrResult?
    )

    suspend fun processImage(rawUri: Uri): PipelineResult = withContext(Dispatchers.IO) {
        var qualityWarning: String? = null
        var ocrResult: OcrResult? = null
        var processedUri: Uri? = null

        try {
            if (!org.opencv.android.OpenCVLoader.initDebug()) {
                Log.e(TAG, "OpenCV init failed")
            }

            var bitmap = ImagePreprocessor.loadSafeBitmap(context, rawUri, 4000)
            
            val quality = ImagePreprocessor.measureImageQuality(bitmap)
            if (quality != ImageQuality.GOOD) {
                qualityWarning = when (quality) {
                    ImageQuality.MOTION_BLUR -> "Hold still! Motion blur detected. Please retake the photo."
                    ImageQuality.TOO_BLURRY -> "Image is too blurry. Please retake the photo for better results."
                    ImageQuality.GLARE -> "Too much glare detected. Please tilt the phone to avoid reflections."
                    ImageQuality.TOO_DARK -> "Image is too dark. Please retake in a brighter environment."
                    ImageQuality.TOO_BRIGHT -> "Image is overexposed. Please retake the photo."
                    else -> null
                }
            }
            
            var processed = DocumentScanner.autoCrop(bitmap, context.cacheDir)
            
            // ML Kit Orientation Detection
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val text = recognizer.process(InputImage.fromBitmap(processed, 0)).await()
                
                val angles = text.textBlocks.mapNotNull { block ->
                    val pts = block.cornerPoints
                    if (pts != null && pts.size == 4) {
                        val dx = pts[1].x.toDouble() - pts[0].x.toDouble()
                        val dy = pts[1].y.toDouble() - pts[0].y.toDouble()
                        Math.toDegrees(Math.atan2(dy, dx))
                    } else null
                }
                
                if (angles.isNotEmpty()) {
                    val medianAngle = angles.sorted()[angles.size / 2]
                    var rotationFix = 0f
                    if (medianAngle > 45 && medianAngle <= 135) {
                        rotationFix = -90f
                    } else if (medianAngle > 135 || medianAngle <= -135) {
                        rotationFix = 180f
                    } else if (medianAngle > -135 && medianAngle <= -45) {
                        rotationFix = 90f
                    }
                    
                    if (rotationFix != 0f) {
                        val matrix = Matrix().apply { postRotate(rotationFix) }
                        processed = Bitmap.createBitmap(processed, 0, 0, processed.width, processed.height, matrix, true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Orientation detection failed", e)
            }
            
            if (processed.height > processed.width) {
                val matrix = Matrix().apply { postRotate(-90f) }
                processed = Bitmap.createBitmap(processed, 0, 0, processed.width, processed.height, matrix, true)
            }

            val file = File(context.cacheDir, "auto_cropped_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                processed.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            processedUri = Uri.fromFile(file)
            
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline processing failed", e)
        }

        return@withContext PipelineResult(processedUri, qualityWarning, ocrResult)
    }
}
