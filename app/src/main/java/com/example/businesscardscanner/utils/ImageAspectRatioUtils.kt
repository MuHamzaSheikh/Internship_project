package com.example.businesscardscanner.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri

object ImageAspectRatioUtils {
    fun getImageSize(context: Context, uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        val width = options.outWidth.takeIf { it > 0 } ?: return 1 to 1
        val height = options.outHeight.takeIf { it > 0 } ?: return 1 to 1
        return width to height
    }

    fun getAspectRatio(context: Context, uri: Uri): Float {
        val (width, height) = getImageSize(context, uri)
        return if (height > 0) width.toFloat() / height.toFloat() else 1f
    }

    fun toConstraintRatio(aspectRatio: Float): String {
        val safe = if (aspectRatio > 0f) aspectRatio else 1f
        return "${safe}:1"
    }
}
