package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY is_favorite DESC, updated_at DESC")
    fun getAllEntriesFlow(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries ORDER BY is_favorite DESC, updated_at DESC")
    suspend fun getAllEntriesSync(): List<VaultEntryEntity>

    @Query("SELECT * FROM vault_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): VaultEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VaultEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<VaultEntryEntity>)

    @Update
    suspend fun updateEntry(entry: VaultEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: VaultEntryEntity)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM vault_entries")
    suspend fun deleteAllEntries()

    @Query("SELECT COUNT(*) FROM vault_entries")
    suspend fun getCount(): Int
}
