package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity where all sensitive fields are stored as AES-256-GCM ciphertexts.
 */
@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title_encrypted")
    val titleEncrypted: String,

    @ColumnInfo(name = "username_encrypted")
    val usernameEncrypted: String,

    @ColumnInfo(name = "password_encrypted")
    val passwordEncrypted: String,

    @ColumnInfo(name = "url_encrypted")
    val urlEncrypted: String,

    @ColumnInfo(name = "notes_encrypted")
    val notesEncrypted: String,

    @ColumnInfo(name = "category")
    val category: String = "Login", // Login, Card, Note, Identity

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
