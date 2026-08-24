package com.janreins.vaultlock.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an item stored in the SQLite database.
 * Crucial fields (username, password, url, notes) are stored as AES-256-GCM encrypted Base64 strings.
 */
@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "encrypted_username")
    val encryptedUsername: String,

    @ColumnInfo(name = "encrypted_password")
    val encryptedPassword: String,

    @ColumnInfo(name = "encrypted_url")
    val encryptedUrl: String,

    @ColumnInfo(name = "encrypted_notes")
    val encryptedNotes: String,

    @ColumnInfo(name = "category")
    val category: String = "Login",

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
