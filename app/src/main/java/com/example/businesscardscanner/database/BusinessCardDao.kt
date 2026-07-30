package com.example.businesscardscanner.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessCardDao {
    @Query("SELECT * FROM business_cards WHERE isInRecycleBin = 0 ORDER BY createdAt DESC")
    fun observeActiveCards(): Flow<List<BusinessCardEntity>>

    @Query("SELECT * FROM business_cards WHERE isInRecycleBin = 1 ORDER BY deletedAt DESC, createdAt DESC")
    fun observeRecycleBin(): Flow<List<BusinessCardEntity>>

    @Query("SELECT * FROM business_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BusinessCardEntity?

    @Query("""
        SELECT * FROM business_cards
        WHERE isInRecycleBin = 0
          AND (
              (:name != '' AND LOWER(name) = LOWER(:name))
              OR (:company != '' AND LOWER(company) = LOWER(:company))
              OR (:phone != '' AND REPLACE(phone, ' ', '') = REPLACE(:phone, ' ', ''))
              OR (:email != '' AND LOWER(email) = LOWER(:email))
          )
        LIMIT 1
    """)
    suspend fun findPotentialDuplicate(
        name: String,
        company: String,
        phone: String,
        email: String
    ): BusinessCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: BusinessCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cards: List<BusinessCardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeCategoryEntities(): Flow<List<CategoryEntity>>

    @Query("SELECT name FROM categories ORDER BY name COLLATE NOCASE ASC")
    suspend fun getCategoryNamesOnce(): List<String>

    @Query("UPDATE business_cards SET isInRecycleBin = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun moveToRecycleBin(id: String, deletedAt: Long)

    @Query("UPDATE business_cards SET isInRecycleBin = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM business_cards WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("UPDATE business_cards SET qrText = :qrText, qrTimestamp = :timestamp WHERE id = :id")
    suspend fun updateQrData(id: String, qrText: String?, timestamp: Long?)

    @Query("UPDATE business_cards SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("SELECT DISTINCT groupName FROM business_cards WHERE groupName != '' ORDER BY groupName COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT groupName FROM business_cards WHERE groupName != ''")
    suspend fun getCategoriesOnce(): List<String>
}
