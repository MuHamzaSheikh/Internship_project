package com.example.businesscardscanner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BusinessCardEntity::class, CategoryEntity::class],
    version = 5,
    exportSchema = false
)
abstract class BusinessCardDatabase : RoomDatabase() {
    abstract fun businessCardDao(): BusinessCardDao

    companion object {
        @Volatile private var INSTANCE: BusinessCardDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_cards ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE business_cards ADD COLUMN ocrText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE business_cards ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE business_cards ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_cards ADD COLUMN backImageUri TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS categories (name TEXT NOT NULL PRIMARY KEY)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_cards RENAME TO business_cards_old")
                db.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS business_cards (
                            id TEXT NOT NULL PRIMARY KEY,
                            imageUri TEXT,
                            backImageUri TEXT,
                            name TEXT NOT NULL,
                            company TEXT NOT NULL,
                            jobTitle TEXT NOT NULL,
                            phone TEXT NOT NULL,
                            email TEXT NOT NULL,
                            website TEXT NOT NULL,
                            address TEXT NOT NULL,
                            groupName TEXT NOT NULL,
                            isFavorite INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            isInRecycleBin INTEGER NOT NULL,
                            qrText TEXT,
                            qrTimestamp INTEGER,
                            deletedAt INTEGER,
                            ocrText TEXT NOT NULL,
                            notes TEXT NOT NULL,
                            category TEXT NOT NULL
                        )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                        INSERT INTO business_cards (
                            id, imageUri, backImageUri, name, company, jobTitle, phone, email,
                            website, address, groupName, isFavorite, createdAt, updatedAt,
                            isInRecycleBin, qrText, qrTimestamp, deletedAt, ocrText, notes, category
                        )
                        SELECT
                            id, imageUri, backImageUri, name, company, jobTitle, phone, email,
                            website, address, groupName, isFavorite, createdAt, updatedAt,
                            isInRecycleBin, qrText, qrTimestamp, deletedAt, ocrText, notes, category
                        FROM business_cards_old
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE business_cards_old")
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_cards RENAME TO business_cards_old")
                db.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS business_cards (
                            id TEXT NOT NULL PRIMARY KEY,
                            imageUri TEXT,
                            backImageUri TEXT,
                            name TEXT NOT NULL,
                            company TEXT NOT NULL,
                            jobTitle TEXT NOT NULL,
                            phone TEXT NOT NULL,
                            email TEXT NOT NULL,
                            website TEXT NOT NULL,
                            address TEXT NOT NULL,
                            groupName TEXT NOT NULL,
                            isFavorite INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            isInRecycleBin INTEGER NOT NULL,
                            qrText TEXT,
                            qrTimestamp INTEGER,
                            deletedAt INTEGER,
                            ocrText TEXT NOT NULL,
                            notes TEXT NOT NULL,
                            category TEXT NOT NULL
                        )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                        INSERT INTO business_cards (
                            id, imageUri, backImageUri, name, company, jobTitle, phone, email,
                            website, address, groupName, isFavorite, createdAt, updatedAt,
                            isInRecycleBin, qrText, qrTimestamp, deletedAt, ocrText, notes, category
                        )
                        SELECT
                            id, imageUri, backImageUri, name, company, jobTitle, phone, email,
                            website, address, groupName, isFavorite, createdAt, updatedAt,
                            isInRecycleBin, qrText, qrTimestamp, deletedAt, ocrText, notes, category
                        FROM business_cards_old
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE business_cards_old")
            }
        }

        fun getInstance(context: Context): BusinessCardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BusinessCardDatabase::class.java,
                    "business_cards.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_5, MIGRATION_4_5).build().also { INSTANCE = it }
            }
        }
    }
}
