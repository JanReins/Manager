package com.janreins.vaultlock.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 27, 28, 29])
class VaultDatabaseMigrationTest {
    @Test
    fun `migration from v1 preserves legacy title and every stored field`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE vault_entries (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                title TEXT NOT NULL,
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
                            INSERT INTO vault_entries VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(), arrayOf<Any>(
                            42L, "Legacy title", "username ciphertext", "password ciphertext",
                            "url ciphertext", "notes ciphertext", "Personal", 1, 123456L, 234567L
                        ))
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("This test runs MIGRATION_1_2 directly on the version 1 database")
                    }
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            assertEquals(1, db.version)
            // Room runs migrations within a transaction; reproduce that here without room-testing.
            db.beginTransaction()
            try {
                VaultDatabase.MIGRATION_1_2.migrate(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(vault_entries)").use { cursor ->
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            assertFalse(columns.contains("title"))
            assertTrue(columns.contains("encrypted_title"))
            assertEquals(setOf(
                "id", "encrypted_title", "encrypted_username", "encrypted_password",
                "encrypted_url", "encrypted_notes", "category", "is_favorite",
                "created_at", "updated_at"
            ), columns)

            db.query("SELECT * FROM vault_entries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
                assertEquals("Legacy title", cursor.getString(cursor.getColumnIndexOrThrow("encrypted_title")))
                assertEquals("username ciphertext", cursor.getString(cursor.getColumnIndexOrThrow("encrypted_username")))
                assertEquals("password ciphertext", cursor.getString(cursor.getColumnIndexOrThrow("encrypted_password")))
                assertEquals("url ciphertext", cursor.getString(cursor.getColumnIndexOrThrow("encrypted_url")))
                assertEquals("notes ciphertext", cursor.getString(cursor.getColumnIndexOrThrow("encrypted_notes")))
                assertEquals("Personal", cursor.getString(cursor.getColumnIndexOrThrow("category")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")))
                assertEquals(123456L, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                assertEquals(234567L, cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            helper.close()
        }
    }
}
