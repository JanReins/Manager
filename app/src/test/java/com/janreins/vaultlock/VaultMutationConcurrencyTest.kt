package com.janreins.vaultlock

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.vaultlock.crypto.SessionManager
import com.janreins.vaultlock.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultMutationConcurrencyTest {
    private lateinit var db: VaultDatabase
    private lateinit var preferences: SecurityPreferences
    private val oldKey = SecretKeySpec(ByteArray(32) { 1 }, "AES")
    private val newKey = SecretKeySpec(ByteArray(32) { 2 }, "AES")
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).allowMainThreadQueries().build()
        preferences = SecurityPreferences(context, context.getSharedPreferences("concurrency", Context.MODE_PRIVATE))
        SessionManager.setKey(oldKey)
    }
    @After fun cleanup() { SessionManager.lock(); db.close() }

    @Test fun `queued old session save cannot escape a rotation snapshot`() = runTest {
        val normal = VaultRepository(db.vaultDao(), preferences)
        normal.saveEntry(VaultEntry(title = "Original", password = "preserve"))
        val snapshotRead = CompletableDeferred<Unit>()
        val resumeRotation = CompletableDeferred<Unit>()
        val gatedDao = object : VaultDao by db.vaultDao() {
            override suspend fun getAllEntriesSync(): List<VaultEntryEntity> {
                val rows = db.vaultDao().getAllEntriesSync()
                snapshotRead.complete(Unit)
                resumeRotation.await()
                return rows
            }
        }
        val rotation = async {
            VaultRepository(gatedDao, preferences).reEncryptAll(oldKey, newKey,
                afterWrite = { SessionManager.setKey(newKey) })
        }
        snapshotRead.await()
        // UNDISPATCHED captures the old generation before waiting on the mutation gate.
        val save = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { normal.saveEntry(VaultEntry(title = "Late old-key write")) }
        }
        resumeRotation.complete(Unit)
        rotation.await()
        assertTrue(save.await().isFailure)
        assertEquals(1, db.vaultDao().getCount())
        assertEquals(true, normal.keyDecryptsVault(newKey))
        assertEquals(false, normal.keyDecryptsVault(oldKey))
    }

    @Test fun `failure after row commit locks before allowing subsequent writes`() = runTest {
        val repository = VaultRepository(db.vaultDao(), preferences)
        repository.saveEntry(VaultEntry(title = "Original"))
        try {
            repository.reEncryptAll(oldKey, newKey, afterWrite = { error("Simulated metadata failure") })
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertFalse(SessionManager.hasKey())
        assertEquals(true, repository.keyDecryptsVault(newKey))
        try { repository.saveEntry(VaultEntry(title = "Must not save")); fail("Vault must be locked") }
        catch (_: IllegalStateException) { }
        assertEquals(1, db.vaultDao().getCount())
    }
}
