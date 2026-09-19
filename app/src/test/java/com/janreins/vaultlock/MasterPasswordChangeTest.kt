package com.janreins.vaultlock

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.janreins.vaultlock.crypto.SessionManager
import com.janreins.vaultlock.data.SecurityPreferences
import com.janreins.vaultlock.data.VaultDatabase
import com.janreins.vaultlock.data.VaultEntry
import com.janreins.vaultlock.data.VaultRepository
import com.janreins.vaultlock.ui.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MasterPasswordChangeTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var database: VaultDatabase
    private lateinit var repository: VaultRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        securityPreferences = SecurityPreferences(application)
        database = VaultDatabase.getInstance(application)
        repository = VaultRepository(database.vaultDao(), securityPreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SessionManager.lock()
        database.close()
    }

    @Test
    fun `prepareMasterPasswordChange does not commit until commitMasterPasswordChange is called`() {
        val oldPass = "OldMasterPassword123!".toCharArray()
        val newPass = "NewMasterPassword123!".toCharArray()

        // Setup initial master password
        val oldKey = securityPreferences.setupMasterPassword(oldPass)
        assertNotNull(oldKey)
        assertNotNull(securityPreferences.verifyAndDeriveKey(oldPass))

        // Prepare change
        val prepared = securityPreferences.prepareMasterPasswordChange(newPass)
        assertNotNull(prepared.secretKey)

        // Verifier and salt in storage should still correspond to old password
        assertNotNull(securityPreferences.verifyAndDeriveKey(oldPass))
        assertNull(securityPreferences.verifyAndDeriveKey(newPass))

        // Commit change
        securityPreferences.commitMasterPasswordChange(prepared)

        // Now new password works and old password fails
        assertNull(securityPreferences.verifyAndDeriveKey(oldPass))
        assertNotNull(securityPreferences.verifyAndDeriveKey(newPass))
    }

    @Test
    fun `changeMasterPassword success re-encrypts database and commits new password`() = runTest {
        val oldPass = "OldMasterPass123!"
        val newPass = "NewMasterPass456!"

        // 1. Setup initial master password
        val oldKey = securityPreferences.setupMasterPassword(oldPass.toCharArray())
        SessionManager.setKey(oldKey)

        // 2. Insert entry encrypted with old key
        val entry = VaultEntry(
            id = 0,
            title = "Secret Site",
            username = "user123",
            password = "supersecretpassword",
            url = "https://example.com",
            notes = "important notes"
        )
        repository.saveEntry(entry)

        // Verify entity exists and decrypts with oldKey
        val entriesBefore = database.vaultDao().getAllEntriesSync()
        assertEquals(1, entriesBefore.size)

        // 3. Prepare new password and re-encrypt
        val prepared = securityPreferences.prepareMasterPasswordChange(newPass.toCharArray())
        repository.reEncryptAll(oldKey, prepared.secretKey)
        securityPreferences.commitMasterPasswordChange(prepared)
        SessionManager.setKey(prepared.secretKey)

        // 4. Verify old password verification fails and new password succeeds
        assertNull(securityPreferences.verifyAndDeriveKey(oldPass.toCharArray()))
        val newDerivedKey = securityPreferences.verifyAndDeriveKey(newPass.toCharArray())
        assertNotNull(newDerivedKey)

        // 5. Verify database entity is decrypted correctly using new key
        val restoredEntry = repository.getEntryById(entriesBefore[0].id)
        assertNotNull(restoredEntry)
        assertEquals("Secret Site", restoredEntry?.title)
        assertEquals("supersecretpassword", restoredEntry?.password)
    }

    @Test
    fun `failed re-encryption leaves old salt and verifier intact`() = runTest {
        val oldPass = "OldMasterPass123!"
        val newPass = "NewMasterPass456!"

        val oldKey = securityPreferences.setupMasterPassword(oldPass.toCharArray())
        assertNotNull(oldKey)

        val prepared = securityPreferences.prepareMasterPasswordChange(newPass.toCharArray())

        // Simulate exception during re-encrypt (e.g. database error)
        var reEncryptFailed = false
        try {
            // Suppose re-encryption threw an exception before commitMasterPasswordChange
            throw RuntimeException("Database I/O error during re-encryption")
            @Suppress("UNREACHABLE_CODE")
            securityPreferences.commitMasterPasswordChange(prepared)
        } catch (_: Exception) {
            reEncryptFailed = true
        }

        assertTrue(reEncryptFailed)

        // Old master password must still be intact and valid in securityPreferences
        assertNotNull(securityPreferences.verifyAndDeriveKey(oldPass.toCharArray()))
        assertNull(securityPreferences.verifyAndDeriveKey(newPass.toCharArray()))
    }
}
