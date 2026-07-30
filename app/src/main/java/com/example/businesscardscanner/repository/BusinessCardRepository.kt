package com.example.businesscardscanner.repository

import android.content.Context
import com.example.businesscardscanner.database.CategoryEntity
import com.example.businesscardscanner.database.BusinessCardDatabase
import com.example.businesscardscanner.database.toEntity
import com.example.businesscardscanner.database.toModel
import com.example.businesscardscanner.models.BusinessCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BusinessCardRepository private constructor(context: Context) {
    private val dao = BusinessCardDatabase.getInstance(context).businessCardDao()

    fun observeActiveCards(): Flow<List<BusinessCard>> = dao.observeActiveCards().map { list -> list.map { it.toModel() } }
    fun observeRecycleBin(): Flow<List<BusinessCard>> = dao.observeRecycleBin().map { list -> list.map { it.toModel() } }
    fun observeCategories(): Flow<List<String>> = dao.observeCategoryEntities().map { entities ->
        (listOf("VIP", "Family", "Colleague", "Recent") + entities.map { it.name }).distinct()
    }

    suspend fun upsert(
        card: BusinessCard,
        qrText: String? = card.qrText,
        qrTimestamp: Long? = card.qrTimestamp,
        ocrText: String = card.ocrText,
        notes: String = card.notes,
        category: String = card.category
    ) {
        dao.upsert(
            card.toEntity(
                qrText = qrText,
                qrTimestamp = qrTimestamp,
                ocrText = ocrText,
                notes = notes,
                category = category
            )
        )
    }

    suspend fun moveToRecycleBin(card: BusinessCard) {
        dao.moveToRecycleBin(card.id, System.currentTimeMillis())
    }

    suspend fun restore(card: BusinessCard) {
        dao.restore(card.id)
    }

    suspend fun deletePermanently(card: BusinessCard) {
        dao.deletePermanently(card.id)
    }

    suspend fun saveCategory(name: String) {
        val value = name.trim()
        if (value.isNotBlank()) {
            dao.upsertCategory(CategoryEntity(value))
        }
    }

    suspend fun updateQrData(cardId: String, qrText: String, timestamp: Long) {
        dao.updateQrData(cardId, qrText, timestamp)
    }

    suspend fun touch(cardId: String) {
        dao.touch(cardId, System.currentTimeMillis())
    }

    suspend fun getById(id: String): BusinessCard? = dao.getById(id)?.toModel()

    suspend fun findPotentialDuplicate(
        name: String,
        company: String,
        phone: String,
        email: String
    ): BusinessCard? = dao.findPotentialDuplicate(name, company, phone, email)?.toModel()

    companion object {
        @Volatile private var INSTANCE: BusinessCardRepository? = null

        fun getInstance(context: Context): BusinessCardRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BusinessCardRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
