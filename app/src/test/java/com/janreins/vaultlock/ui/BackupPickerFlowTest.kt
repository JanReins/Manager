package com.janreins.vaultlock.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.janreins.vaultlock.crypto.SessionManager
import com.janreins.vaultlock.data.SecurityPreferences
import com.janreins.vaultlock.data.VaultDatabase
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
class BackupPickerFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val uri = Uri.parse("content://backup-test/backup.vault")
    private lateinit var application: Application
    private lateinit var viewModel: VaultViewModel
    private lateinit var store: ViewModelStore
    private lateinit var documents: FakeDocumentStore

    private class FakeDocumentStore : BackupDocumentStore {
        var input = ByteArray(40) { (it + 1).toByte() }
        var written: ByteArray? = null
        var writtenUri: Uri? = null
        var deletedUri: Uri? = null
        var failWrite = false
        var failDelete = false

        override fun write(uri: Uri, bytes: ByteArray) {
            if (failWrite) throw IOException("Cannot write")
            written = bytes.copyOf()
            writtenUri = uri
        }

        override fun delete(uri: Uri): Boolean {
            deletedUri = uri
            if (failDelete) throw IOException("Cannot delete")
            return true
        }

        override fun read(uri: Uri): ByteArray = ByteArrayInputStream(input).use {
            BackupFileIO.readBounded(it)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
        val prefs = application.getSharedPreferences("backup_picker_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SessionManager.setKey(SecretKeySpec(ByteArray(32) { 1 }, "AES"))
        documents = FakeDocumentStore()
        viewModel = VaultViewModel(application, SecurityPreferences(application, customPrefs = prefs),
            dispatcher, elapsedRealtime = { 1_000L }, backupDocumentStore = documents)
        store = ViewModelStore().apply { put("vault", viewModel) }
    }

    @After
    fun tearDown() {
        store.clear()
        SessionManager.lock()
        Dispatchers.resetMain()
    }

    // Inspect ownership/zeroing without adding a production API that exposes backup bytes.
    private fun pendingBytes(name: String): ByteArray? =
        VaultViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
            .get(viewModel) as ByteArray?

    private fun seedExport(bytes: ByteArray) {
        VaultViewModel::class.java.getDeclaredField("pendingExport").apply { isAccessible = true }
            .set(viewModel, bytes)
    }

    /** Pump Main while real IO finishes; never advance the repeating inactivity timer. */
    private suspend fun TestScope.awaitState(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (true) {
            runCurrent()
            if (condition()) return
            check(System.nanoTime() < deadline) { "Timed out waiting for backup IO" }
            withContext(Dispatchers.IO) { delay(1) }
        }
    }

    @Test
    fun `export prepared in VM survives lock and writes exact ciphertext then zeroes`() = runTest {
        runCurrent()
        val password = "SeparateBackupPassword!".toCharArray()
        viewModel.exportBackup(password)
        assertTrue(viewModel.uiState.value.isBackupBusy)
        awaitState { viewModel.uiState.value.exportReadyFileName != null }
        assertTrue(password.all { it == '\u0000' })
        val vmBytes = checkNotNull(pendingBytes("pendingExport"))
        val expectedCiphertext = vmBytes.copyOf()
        assertNotNull(viewModel.uiState.value.exportReadyFileName)
        viewModel.consumeExportReadyFileName()
        viewModel.lockVault()
        assertFalse(SessionManager.hasKey())
        assertArrayEquals(expectedCiphertext, pendingBytes("pendingExport"))

        viewModel.completeExport(uri)
        awaitState { !viewModel.uiState.value.isBackupBusy }
        assertEquals(uri, documents.writtenUri)
        assertArrayEquals(expectedCiphertext, documents.written)
        assertTrue(vmBytes.all { it == 0.toByte() })
        assertNull(pendingBytes("pendingExport"))
        assertEquals("Encrypted backup saved", viewModel.uiState.value.userMessage)
        assertFalse(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun `export cancellation zeroes pending bytes and clears suppression`() = runTest {
        runCurrent()
        val bytes = byteArrayOf(1, 2, 3, 4)
        seedExport(bytes)
        viewModel.suppressNextBackgroundLock()
        viewModel.completeExport(null)
        assertTrue(bytes.all { it == 0.toByte() })
        assertNull(pendingBytes("pendingExport"))
        assertFalse(viewModel.uiState.value.isBackupBusy)
        assertNull(documents.written)
        viewModel.onActivityStopped(false)
        assertFalse(SessionManager.hasKey())
    }

    @Test
    fun `missing export data deletes empty document and reports lost data even if delete fails`() = runTest {
        runCurrent()
        documents.failDelete = true
        viewModel.lockVault()
        viewModel.completeExport(uri)
        awaitState { !viewModel.uiState.value.isBackupBusy }
        assertEquals(uri, documents.deletedUri)
        assertNull(documents.written)
        assertEquals("Backup was not saved: the encrypted data was lost. Unlock and export again.",
            viewModel.uiState.value.userMessage)
    }

    @Test
    fun `write failure deletes document reports error and zeroes bytes`() = runTest {
        runCurrent()
        val bytes = byteArrayOf(1, 2, 3, 4)
        seedExport(bytes)
        documents.failWrite = true
        viewModel.completeExport(uri)
        awaitState { !viewModel.uiState.value.isBackupBusy }
        assertEquals(uri, documents.deletedUri)
        assertTrue(bytes.all { it == 0.toByte() })
        assertEquals("Backup could not be saved. Try another destination.", viewModel.uiState.value.userMessage)
    }

    @Test
    fun `picker launch failure clears suppression busy state and export bytes`() = runTest {
        runCurrent()
        val bytes = byteArrayOf(1, 2, 3, 4)
        seedExport(bytes)
        viewModel.suppressNextBackgroundLock()
        viewModel.onBackupPickerLaunchFailed(export = true)
        assertTrue(bytes.all { it == 0.toByte() })
        assertNull(pendingBytes("pendingExport"))
        assertFalse(viewModel.uiState.value.isBackupBusy)
        viewModel.onActivityStopped(false)
        assertFalse(SessionManager.hasKey())
    }

    @Test
    fun `import result clears suppression before reading`() = runTest {
        runCurrent()
        assertTrue(viewModel.beginImportPicker())
        viewModel.suppressNextBackgroundLock()
        viewModel.onImportFilePicked(uri)
        viewModel.onActivityStopped(false)
        assertFalse(SessionManager.hasKey())
        awaitState { viewModel.uiState.value.hasPendingImport }
        assertArrayEquals(documents.input, pendingBytes("pendingImport"))
    }

    @Test
    fun `null import result clears suppression and busy state`() = runTest {
        runCurrent()
        viewModel.beginImportPicker()
        viewModel.suppressNextBackgroundLock()
        viewModel.onImportFilePicked(null)
        assertFalse(viewModel.uiState.value.isBackupBusy)
        assertFalse(viewModel.uiState.value.hasPendingImport)
        viewModel.onActivityStopped(false)
        assertFalse(SessionManager.hasKey())
    }

    @Test
    fun `import picked while locked holds only encrypted data waits for unlock and discard zeroes`() = runTest {
        runCurrent()
        val dao = VaultDatabase.getInstance(application).vaultDao()
        val countBefore = withContext(Dispatchers.IO) { dao.getCount() }
        viewModel.lockVault()
        viewModel.onImportFilePicked(uri)
        awaitState { viewModel.uiState.value.hasPendingImport }
        val vmBytes = checkNotNull(pendingBytes("pendingImport"))
        assertArrayEquals(documents.input, vmBytes)
        assertEquals(countBefore, withContext(Dispatchers.IO) { dao.getCount() })
        assertNull(viewModel.uiState.value.userMessage)
        assertFalse(viewModel.uiState.value.isUnlocked && viewModel.uiState.value.hasPendingImport)

        // Confirmation while locked cannot reach the restore path either.
        val password = "backup password".toCharArray()
        viewModel.confirmPendingImport(password, legacy = false, skipDuplicates = true)
        assertTrue(password.all { it == '\u0000' })
        assertArrayEquals(documents.input, vmBytes)
        SessionManager.setKey(SecretKeySpec(ByteArray(32) { 1 }, "AES"))
        runCurrent()
        assertTrue(viewModel.uiState.value.isUnlocked && viewModel.uiState.value.hasPendingImport)
        viewModel.discardPendingImport()
        assertTrue(vmBytes.all { it == 0.toByte() })
        assertNull(pendingBytes("pendingImport"))
        assertFalse(viewModel.uiState.value.hasPendingImport)
        assertFalse(viewModel.uiState.value.isBackupBusy)
    }

    @Test
    fun `bounded import read failure reports validation error and clears busy`() = runTest {
        runCurrent()
        documents.input = byteArrayOf(1, 2, 3)
        viewModel.onImportFilePicked(uri)
        awaitState { !viewModel.uiState.value.isBackupBusy }
        assertFalse(viewModel.uiState.value.hasPendingImport)
        assertNull(pendingBytes("pendingImport"))
        assertEquals("File is too short to be an encrypted backup.", viewModel.uiState.value.userMessage)
    }

    @Test
    fun `confirmation after unlock uses pending import and clears it on restore failure`() = runTest {
        runCurrent()
        viewModel.lockVault()
        viewModel.onImportFilePicked(uri)
        awaitState { viewModel.uiState.value.hasPendingImport }
        val imported = checkNotNull(pendingBytes("pendingImport"))
        SessionManager.setKey(SecretKeySpec(ByteArray(32) { 1 }, "AES"))
        runCurrent()
        val password = "backup password".toCharArray()
        viewModel.confirmPendingImport(password, legacy = false, skipDuplicates = true)
        awaitState { !viewModel.uiState.value.isBackupBusy }
        assertFalse(viewModel.uiState.value.hasPendingImport)
        assertNull(pendingBytes("pendingImport"))
        assertTrue(imported.all { it == 0.toByte() })
        assertTrue(password.all { it == '\u0000' })
        assertTrue(checkNotNull(viewModel.uiState.value.userMessage).startsWith("Restore failed:"))
    }

    @Test
    fun `wipe clears pending buffers and editor drafts`() = runTest {
        runCurrent()
        val exported = byteArrayOf(1, 2, 3, 4)
        seedExport(exported)
        viewModel.onImportFilePicked(uri)
        awaitState { viewModel.uiState.value.hasPendingImport }
        val imported = checkNotNull(pendingBytes("pendingImport"))
        val draft = viewModel.entryDraft("editor").apply { password = "unsaved secret" }
        var completed = false
        viewModel.wipeAllData { completed = true }
        assertTrue(exported.all { it == 0.toByte() })
        assertTrue(imported.all { it == 0.toByte() })
        assertEquals("", draft.password)
        assertNull(pendingBytes("pendingExport"))
        assertNull(pendingBytes("pendingImport"))
        assertFalse(viewModel.uiState.value.hasPendingImport)
        assertFalse(viewModel.uiState.value.isBackupBusy)
        awaitState { completed }
        assertFalse(SessionManager.hasKey())
    }

    @Test
    fun `onCleared zeroes both pending buffers`() = runTest {
        runCurrent()
        val exported = byteArrayOf(1, 2, 3, 4)
        seedExport(exported)
        viewModel.onImportFilePicked(uri)
        awaitState { viewModel.uiState.value.hasPendingImport }
        val imported = checkNotNull(pendingBytes("pendingImport"))
        store.clear()
        assertTrue(exported.all { it == 0.toByte() })
        assertTrue(imported.all { it == 0.toByte() })
        assertNull(pendingBytes("pendingExport"))
        assertNull(pendingBytes("pendingImport"))
    }
}
