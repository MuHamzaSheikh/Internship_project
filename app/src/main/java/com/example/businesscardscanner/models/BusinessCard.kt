package com.example.businesscardscanner.models

import android.net.Uri
import java.util.UUID

data class BusinessCard(
    val id: String = UUID.randomUUID().toString(),
    val imageUri: Uri? = null,
    val backImageUri: Uri? = null,
    val name: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val address: String = "",
    val group: String = "VIP",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isInRecycleBin: Boolean = false,
    val qrText: String? = null,
    val qrTimestamp: Long? = null,
    val ocrText: String = "",
    val notes: String = "",
    val description: String = "",
    val phoneSecondary: String = "",
    val category: String = ""
)
