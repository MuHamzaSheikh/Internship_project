package com.example.businesscardscanner.database

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.businesscardscanner.models.BusinessCard

@Entity(tableName = "business_cards")
data class BusinessCardEntity(
    @PrimaryKey val id: String,
    val imageUri: String?,
    val backImageUri: String?,
    val name: String,
    val company: String,
    val jobTitle: String,
    val phone: String,
    val email: String,
    val website: String,
    val address: String,
    val groupName: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val isInRecycleBin: Boolean,
    val qrText: String?,
    val qrTimestamp: Long?,
    val deletedAt: Long?,
    val ocrText: String,
    val notes: String,
    val description: String,
    val phoneSecondary: String,
    val category: String
)

fun BusinessCardEntity.toModel() = BusinessCard(
    id = id,
    imageUri = imageUri?.let(Uri::parse),
    backImageUri = backImageUri?.let(Uri::parse),
    name = name,
    company = company,
    jobTitle = jobTitle,
    phone = phone,
    email = email,
    website = website,
    address = address,
    group = groupName,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isInRecycleBin = isInRecycleBin,
    qrText = qrText,
    qrTimestamp = qrTimestamp,
    ocrText = ocrText,
    notes = notes,
    description = description,
    phoneSecondary = phoneSecondary,
    category = category
)

fun BusinessCard.toEntity(
    qrText: String? = this.qrText,
    qrTimestamp: Long? = this.qrTimestamp,
    deletedAt: Long? = null,
    ocrText: String = this.ocrText,
    notes: String = this.notes,
    description: String = this.description,
    phoneSecondary: String = this.phoneSecondary,
    category: String = this.category
) = BusinessCardEntity(
    id = id,
    imageUri = imageUri?.toString(),
    backImageUri = backImageUri?.toString(),
    name = name,
    company = company,
    jobTitle = jobTitle,
    phone = phone,
    email = email,
    website = website,
    address = address,
    groupName = group,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isInRecycleBin = isInRecycleBin,
    qrText = qrText,
    qrTimestamp = qrTimestamp,
    deletedAt = deletedAt,
    ocrText = ocrText,
    notes = notes,
    description = description,
    phoneSecondary = phoneSecondary,
    category = category
)
