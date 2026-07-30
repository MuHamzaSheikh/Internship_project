package com.example.businesscardscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImagePreprocessor {
    suspend fun loadAndEnhance(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Unable to decode image")

        val oriented = rotateIfNeeded(context, uri, original)
        val normalized = if (oriented.width < 1600 || oriented.height < 1600) {
            Bitmap.createScaledBitmap(oriented, oriented.width.coerceAtLeast(1600), oriented.height.coerceAtLeast(1600), true)
        } else oriented

        enhance(normalized)
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

    private fun enhance(bitmap: Bitmap): Bitmap {
        val matrix = ColorMatrix().apply {
            setScale(1.08f, 1.08f, 1.08f, 1f)
            setConcat(this, ColorMatrix().apply {
                set(floatArrayOf(
                    1.15f, 0f, 0f, 0f, -10f,
                    0f, 1.15f, 0f, 0f, -10f,
                    0f, 0f, 1.15f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
            })
        }
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}
