package com.example.businesscardscanner.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CameraUtils {
    private const val TAG = "CardScannerCamera"

    suspend fun captureToMediaStore(
        context: Context,
        imageCapture: ImageCapture
    ): Uri = suspendCancellableCoroutine { continuation ->
        Log.d(TAG, "ImageCapture.takePicture invoked capture=$imageCapture")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "business_card_${System.currentTimeMillis()}")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val output = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
        imageCapture.takePicture(
            output,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "ImageCapture.onImageSaved savedUri=${outputFileResults.savedUri}")
                    continuation.resume(outputFileResults.savedUri ?: Uri.EMPTY)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "ImageCapture.onError code=${exception.imageCaptureError}", exception)
                    continuation.resumeWithException(exception)
                }
            }
        )
    }
}
