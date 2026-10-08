package com.janreins.vaultlock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VaultEntryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        /**
         * Migration from version 1 to 2.
         * Renames unencrypted/legacy 'title' column to 'encrypted_title' if present,
         * or ensures 'encrypted_title' column exists without losing existing records.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(vault_entries)")
                var hasTitle = false
                var hasEncryptedTitle = false
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1) {
                        val colName = cursor.getString(nameIndex)
                        if (colName == "title") hasTitle = true
                        if (colName == "encrypted_title") hasEncryptedTitle = true
                    }
                }
                cursor.close()

                if (hasTitle && !hasEncryptedTitle) {
                    // Rebuild the v2 table: RENAME COLUMN is unavailable on API 26–29.
                    db.execSQL("""
                        CREATE TABLE vault_entries_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            encrypted_title TEXT NOT NULL,
                            encrypted_username TEXT NOT NULL,
                            encrypted_password TEXT NOT NULL,
                            encrypted_url TEXT NOT NULL,
                            encrypted_notes TEXT NOT NULL,
                            category TEXT NOT NULL,
                            is_favorite INTEGER NOT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        INSERT INTO vault_entries_new (
                            id, encrypted_title, encrypted_username, encrypted_password,
                            encrypted_url, encrypted_notes, category, is_favorite,
                            created_at, updated_at
                        )
                        SELECT id, title, encrypted_username, encrypted_password,
                            encrypted_url, encrypted_notes, category, is_favorite,
                            created_at, updated_at
                        FROM vault_entries
                    """.trimIndent())
                    db.execSQL("DROP TABLE vault_entries")
                    db.execSQL("ALTER TABLE vault_entries_new RENAME TO vault_entries")
                } else if (!hasEncryptedTitle) {
                    db.execSQL("ALTER TABLE vault_entries ADD COLUMN encrypted_title TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /**
         * Migration from version 2 to 3.
         * Adds encrypted_totp_secret column to vault_entries table.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(vault_entries)")
                var hasTotpSecret = false
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1) {
                        val colName = cursor.getString(nameIndex)
                        if (colName == "encrypted_totp_secret") hasTotpSecret = true
                    }
                }
                cursor.close()

                if (!hasTotpSecret) {
                    db.execSQL("ALTER TABLE vault_entries ADD COLUMN encrypted_totp_secret TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vaultlock_secure.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
