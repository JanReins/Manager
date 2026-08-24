package com.example.data

import com.example.crypto.CryptoManager
import com.example.crypto.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

class VaultRepository(
    private val vaultDao: VaultDao,
    private val securityPrefs: SecurityPreferences
) {
    /**
     * Reactive stream of decrypted vault entries.
     * Decrypts in background IO dispatcher.
     */
    val allEntries: Flow<List<VaultEntry>> = vaultDao.getAllEntriesFlow()
        .map { entities ->
            if (!SessionManager.hasKey()) {
                emptyList()
            } else {
                val key = SessionManager.getKey()
                entities.mapNotNull { entity ->
                    try {
                        decryptEntity(entity, key)
                    } catch (e: Exception) {
                        // Skip corrupted or un-decryptable entry
                        null
                    }
                }
            }
        }
        .flowOn(Dispatchers.IO)

    suspend fun getEntryById(id: Long): VaultEntry? = withContext(Dispatchers.IO) {
        val entity = vaultDao.getEntryById(id) ?: return@withContext null
        val key = SessionManager.getKey()
        decryptEntity(entity, key)
    }

    suspend fun saveEntry(entry: VaultEntry): Long = withContext(Dispatchers.IO) {
        val key = SessionManager.getKey()
        val entity = encryptEntry(entry, key)
        if (entry.id == 0L) {
            vaultDao.insertEntry(entity)
        } else {
            vaultDao.updateEntry(entity)
            entry.id
        }
    }

    suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        vaultDao.deleteEntryById(id)
    }

    suspend fun toggleFavorite(id: Long) = withContext(Dispatchers.IO) {
        val entity = vaultDao.getEntryById(id) ?: return@withContext
        val updated = entity.copy(
            isFavorite = !entity.isFavorite,
            updatedAt = System.currentTimeMillis()
        )
        vaultDao.updateEntry(updated)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        vaultDao.deleteAllEntries()
        securityPrefs.wipeAll()
        SessionManager.lock()
    }

    /**
     * Re-encrypts all database records with a new Master Key when changing password.
     */
    suspend fun reEncryptAll(oldKey: SecretKey, newKey: SecretKey) = withContext(Dispatchers.IO) {
        val allEntities = vaultDao.getAllEntriesSync()
        val reEncrypted = allEntities.map { entity ->
            val decrypted = decryptEntity(entity, oldKey)
            encryptEntry(decrypted, newKey)
        }
        vaultDao.insertAll(reEncrypted)
    }

    /**
     * Exports an encrypted backup package.
     * Encrypted using the current session Master Key with AES-256-GCM.
     */
    suspend fun exportEncryptedBackup(): ByteArray = withContext(Dispatchers.IO) {
        val key = SessionManager.getKey()
        val allEntities = vaultDao.getAllEntriesSync()
        val jsonArray = JSONArray()

        for (entity in allEntities) {
            val decrypted = decryptEntity(entity, key)
            val obj = JSONObject().apply {
                put("title", decrypted.title)
                put("username", decrypted.username)
                put("password", decrypted.password)
                put("url", decrypted.url)
                put("notes", decrypted.notes)
                put("category", decrypted.category)
                put("isFavorite", decrypted.isFavorite)
                put("createdAt", decrypted.createdAt)
                put("updatedAt", decrypted.updatedAt)
            }
            jsonArray.put(obj)
        }

        val rootObject = JSONObject().apply {
            put("app", "VaultLock")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("entries", jsonArray)
        }

        val rawJsonBytes = rootObject.toString().toByteArray(Charsets.UTF_8)
        CryptoManager.encryptBytes(rawJsonBytes, key)
    }

    /**
     * Imports an encrypted backup package using the current Master Key.
     * Returns the count of imported records.
     */
    suspend fun importEncryptedBackup(encryptedBackupBytes: ByteArray): Int = withContext(Dispatchers.IO) {
        val key = SessionManager.getKey()
        val decryptedBytes = CryptoManager.decryptBytes(encryptedBackupBytes, key)
        val jsonString = String(decryptedBytes, Charsets.UTF_8)
        val root = JSONObject(jsonString)
        val entriesArray = root.getJSONArray("entries")

        val newEntities = mutableListOf<VaultEntryEntity>()
        for (i in 0 until entriesArray.length()) {
            val item = entriesArray.getJSONObject(i)
            val entry = VaultEntry(
                id = 0,
                title = item.optString("title", ""),
                username = item.optString("username", ""),
                password = item.optString("password", ""),
                url = item.optString("url", ""),
                notes = item.optString("notes", ""),
                category = item.optString("category", "Login"),
                isFavorite = item.optBoolean("isFavorite", false),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
            newEntities.add(encryptEntry(entry, key))
        }

        if (newEntities.isNotEmpty()) {
            vaultDao.insertAll(newEntities)
        }
        newEntities.size
    }

    private fun encryptEntry(entry: VaultEntry, key: SecretKey): VaultEntryEntity {
        return VaultEntryEntity(
            id = entry.id,
            titleEncrypted = CryptoManager.encrypt(entry.title, key),
            usernameEncrypted = CryptoManager.encrypt(entry.username, key),
            passwordEncrypted = CryptoManager.encrypt(entry.password, key),
            urlEncrypted = CryptoManager.encrypt(entry.url, key),
            notesEncrypted = CryptoManager.encrypt(entry.notes, key),
            category = entry.category,
            isFavorite = entry.isFavorite,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt
        )
    }

    private fun decryptEntity(entity: VaultEntryEntity, key: SecretKey): VaultEntry {
        return VaultEntry(
            id = entity.id,
            title = CryptoManager.decrypt(entity.titleEncrypted, key),
            username = CryptoManager.decrypt(entity.usernameEncrypted, key),
            password = CryptoManager.decrypt(entity.passwordEncrypted, key),
            url = CryptoManager.decrypt(entity.urlEncrypted, key),
            notes = CryptoManager.decrypt(entity.notesEncrypted, key),
            category = entity.category,
            isFavorite = entity.isFavorite,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
