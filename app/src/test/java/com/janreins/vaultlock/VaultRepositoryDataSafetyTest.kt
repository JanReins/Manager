package com.janreins.vaultlock

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.vaultlock.crypto.SessionManager
import com.janreins.vaultlock.data.SecurityPreferences
import com.janreins.vaultlock.data.VaultDatabase
import com.janreins.vaultlock.data.VaultDecryptionException
import com.janreins.vaultlock.data.VaultEntry
import com.janreins.vaultlock.data.VaultRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultRepositoryDataSafetyTest {
    private lateinit var database: VaultDatabase
    private lateinit var repository: VaultRepository
    private val key = SecretKeySpec(ByteArray(32) { 1 }, "AES")
    private val otherKey = SecretKeySpec(ByteArray(32) { 2 }, "AES")
    private val newKey = SecretKeySpec(ByteArray(32) { 3 }, "AES")

    @Before
    fun setUp() {
        SessionManager.lock()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries().build()
        val prefs = context.getSharedPreferences("data_safety_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = VaultRepository(database.vaultDao(), SecurityPreferences(context, prefs))
    }

    @After
    fun tearDown() {
        SessionManager.lock()
        database.close()
    }

    @Test
    fun `entries react to unlock and lock without database writes`() = runTest {
        SessionManager.setKey(key)
        repository.saveEntry(VaultEntry(title = "Secret", password = "password"))
        SessionManager.lock()
        val emissions = Channel<List<VaultEntry>>(Channel.UNLIMITED)
        val collector = launch { repository.getAllEntries().collect { emissions.send(it) } }
        try {
            assertTrue(emissions.receive().isEmpty())
            SessionManager.setKey(key)
            val unlocked = emissions.receive()
            assertEquals("Secret", unlocked.single().title)
            assertEquals("password", unlocked.single().password)
            SessionManager.lock()
            assertTrue(emissions.receive().isEmpty())
        } finally {
            collector.cancel()
            emissions.close()
        }
    }

    private suspend fun seedMixedKeys() {
        SessionManager.setKey(key)
        repository.saveEntry(VaultEntry(title = "Readable", password = "keep me"))
        SessionManager.setKey(otherKey)
        repository.saveEntry(VaultEntry(title = "Unreadable", password = "also keep me"))
        SessionManager.setKey(key)
    }

    private suspend fun expectDecryptionFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected strict decryption to fail")
        } catch (e: VaultDecryptionException) {
            assertTrue(e.message!!.contains("Original data has been preserved"))
        }
    }

    @Test
    fun `failed rotation leaves all ciphertext unchanged`() = runTest {
        seedMixedKeys()
        val before = database.vaultDao().getAllEntriesSync().sortedBy { it.id }
        expectDecryptionFailure { repository.reEncryptAll(key, newKey) }
        assertEquals(before, database.vaultDao().getAllEntriesSync().sortedBy { it.id })
    }

    @Test
    fun `export refuses unreadable rows`() = runTest {
        seedMixedKeys()
        expectDecryptionFailure { repository.createEncryptedBackupPayload("BackupPassword123!".toCharArray()) }
    }

    @Test
    fun `save refuses to overwrite unreadable row`() = runTest {
        SessionManager.setKey(otherKey)
        repository.saveEntry(VaultEntry(title = "Original", password = "preserve"))
        val before = database.vaultDao().getAllEntriesSync().single()
        SessionManager.setKey(key)
        val placeholder = repository.getEntryById(before.id)!!
        assertEquals("[Decryption Failed]", placeholder.title)
        expectDecryptionFailure { repository.saveEntry(placeholder.copy(title = "Edited")) }
        assertEquals(before, database.vaultDao().getEntryById(before.id))
    }

    @Test
    fun `save updates readable entry and preserves fields`() = runTest {
        SessionManager.setKey(key)
        repository.saveEntry(VaultEntry(title = "Original", password = "old"))
        val original = repository.getAllEntries().first().single()
        val updated = original.copy(title = "Edited", username = "user", password = "new",
            url = "https://example.com", notes = "notes", totpSecret = "JBSWY3DP",
            isFavorite = true)
        repository.saveEntry(updated)
        val restored = repository.getEntryById(original.id)!!
        assertEquals(updated.copy(updatedAt = restored.updatedAt), restored)
    }
}
