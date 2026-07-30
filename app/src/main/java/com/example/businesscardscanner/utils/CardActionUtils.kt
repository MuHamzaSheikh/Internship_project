package com.example.businesscardscanner.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.businesscardscanner.models.BusinessCard
import java.io.OutputStream

object CardActionUtils {
    suspend fun saveCardImageToGallery(context: Context, card: BusinessCard): Boolean {
        val uri = card.imageUri ?: return false
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "business_card_${card.id}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BusinessCardScanner")
                }
            }
            val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create media entry")
            resolver.openOutputStream(target).use { out ->
                if (out == null) error("Unable to open gallery output stream")
                copyImage(context, uri, out)
            }
            true
        }.getOrElse { false }
    }

    private fun copyImage(context: Context, source: Uri, output: OutputStream) {
        val sourceStream = context.contentResolver.openInputStream(source) ?: error("Unable to open source image")
        sourceStream.use { input -> input.copyTo(output) }
    }

    fun shareCard(context: Context, card: BusinessCard): Intent {
        val text = buildString {
            append(card.name)
            if (card.company.isNotBlank()) append("\n").append(card.company)
            if (card.jobTitle.isNotBlank()) append("\n").append(card.jobTitle)
            if (card.phone.isNotBlank()) append("\n").append(card.phone)
            if (card.email.isNotBlank()) append("\n").append(card.email)
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            card.imageUri?.let { putExtra(Intent.EXTRA_STREAM, it) }
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
