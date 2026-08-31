package com.janreins.vaultlock.data

import com.janreins.vaultlock.crypto.CryptoManager
import com.janreins.vaultlock.crypto.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

class VaultRepository(
    private val vaultDao: VaultDao,
    private val securityPreferences: SecurityPreferences
) {
    /**
     * Decrypts database entities into domain models in real-time when the vault session is unlocked.
     */
    fun getAllEntries(): Flow<List<VaultEntry>> {
        return vaultDao.getAllEntriesFlow().map { entities ->
            val key = if (SessionManager.hasKey()) SessionManager.getKey() else null
            entities.map { entity ->
                entity.toDomain(key)
            }
        }
    }

    suspend fun getEntryById(id: Long): VaultEntry? = withContext(Dispatchers.IO) {
        val entity = vaultDao.getEntryById(id) ?: return@withContext null
        val key = if (SessionManager.hasKey()) SessionManager.getKey() else null
        entity.toDomain(key)
    }

    suspend fun saveEntry(entry: VaultEntry) = withContext(Dispatchers.IO) {
        val key = SessionManager.getKey()
        val entity = entry.toEntity(key)
        if (entry.id == 0L) {
            vaultDao.insertEntry(entity)
        } else {
            vaultDao.updateEntry(entity)
        }
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val entity = vaultDao.getEntryById(id) ?: return@withContext
        vaultDao.updateEntry(entity.copy(isFavorite = isFavorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        vaultDao.deleteEntryById(id)
    }

    suspend fun reEncryptAll(oldKey: SecretKey, newKey: SecretKey) = withContext(Dispatchers.IO) {
        val entities = vaultDao.getAllEntriesSync()
        val updated = entities.map { entity ->
            val domain = entity.toDomain(oldKey)
            domain.toEntity(newKey)
        }
        vaultDao.insertAll(updated)
    }

    suspend fun createEncryptedBackupPayload(): ByteArray = withContext(Dispatchers.IO) {
        val key = SessionManager.getKey()
        val entities = vaultDao.getAllEntriesSync()
        val itemsArray = JSONArray()

        for (entity in entities) {
            val domain = entity.toDomain(key)
            val jsonItem = JSONObject().apply {
                put("id", domain.id)
                put("title", domain.title)
                put("username", domain.username)
                put("password", domain.password)
                put("url", domain.url)
                put("notes", domain.notes)
                put("category", domain.category)
                put("isFavorite", domain.isFavorite)
                put("createdAt", domain.createdAt)
                put("updatedAt", domain.updatedAt)
            }
            itemsArray.put(jsonItem)
        }

        val backupRoot = JSONObject().apply {
            put("version", 1)
            put("app", "VaultLock")
            put("timestamp", System.currentTimeMillis())
            put("items", itemsArray)
        }

        val rawBytes = backupRoot.toString().toByteArray(Charsets.UTF_8)
        CryptoManager.encryptBytes(rawBytes, key)
    }

    suspend fun restoreEncryptedBackupPayload(encryptedBytes: ByteArray): Int = withContext(Dispatchers.IO) {
        val key = SessionManager.getKey()
        val decryptedBytes = CryptoManager.decryptBytes(encryptedBytes, key)
        val jsonString = String(decryptedBytes, Charsets.UTF_8)
        val root = JSONObject(jsonString)

        val itemsArray = root.getJSONArray("items")
        val restoredEntries = mutableListOf<VaultEntryEntity>()

        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.getJSONObject(i)
            val entry = VaultEntry(
                id = 0, // Generate new IDs on restore
                title = obj.getString("title"),
                username = obj.optString("username", ""),
                password = obj.optString("password", ""),
                url = obj.optString("url", ""),
                notes = obj.optString("notes", ""),
                category = obj.optString("category", "Login"),
                isFavorite = obj.optBoolean("isFavorite", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = System.currentTimeMillis()
            )
            restoredEntries.add(entry.toEntity(key))
        }

        vaultDao.insertAll(restoredEntries)
        restoredEntries.size
    }

    suspend fun wipeEverything() = withContext(Dispatchers.IO) {
        vaultDao.deleteAllEntries()
        securityPreferences.wipeAll()
        SessionManager.lock()
    }

    private fun VaultEntryEntity.toDomain(key: SecretKey?): VaultEntry {
        if (key == null) {
            return VaultEntry(
                id = id,
                title = "••••",
                username = "••••",
                password = "••••",
                url = "",
                notes = "",
                category = category,
                isFavorite = isFavorite,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }

        return try {
            VaultEntry(
                id = id,
                title = CryptoManager.decrypt(encryptedTitle, key),
                username = CryptoManager.decrypt(encryptedUsername, key),
                password = CryptoManager.decrypt(encryptedPassword, key),
                url = CryptoManager.decrypt(encryptedUrl, key),
                notes = CryptoManager.decrypt(encryptedNotes, key),
                category = category,
                isFavorite = isFavorite,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (_: Exception) {
            VaultEntry(
                id = id,
                title = "[Decryption Failed]",
                username = "",
                password = "",
                url = "",
                notes = "",
                category = category,
                isFavorite = isFavorite,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    private fun VaultEntry.toEntity(key: SecretKey): VaultEntryEntity {
        return VaultEntryEntity(
            id = id,
            encryptedTitle = CryptoManager.encrypt(title.trim(), key),
            encryptedUsername = CryptoManager.encrypt(username, key),
            encryptedPassword = CryptoManager.encrypt(password, key),
            encryptedUrl = CryptoManager.encrypt(url.trim(), key),
            encryptedNotes = CryptoManager.encrypt(notes, key),
            category = category,
            isFavorite = isFavorite,
            createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
