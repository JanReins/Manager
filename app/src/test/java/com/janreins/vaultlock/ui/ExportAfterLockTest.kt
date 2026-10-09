package com.janreins.vaultlock.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.janreins.vaultlock.crypto.SessionManager
import com.janreins.vaultlock.data.SecurityPreferences
import com.janreins.vaultlock.data.VaultAccess
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ExportAfterLockTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        SessionManager.lock()
        Dispatchers.resetMain()
    }

    @Test
    fun `lock during backup derivation discards payload without requesting a save picker`() = runTest {
        val app: Application = ApplicationProvider.getApplicationContext()
        val prefs = app.getSharedPreferences("export_after_lock_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SessionManager.setKey(SecretKeySpec(ByteArray(32) { 1 }, "AES"))
        val vm = VaultViewModel(app, SecurityPreferences(app, customPrefs = prefs), dispatcher)
        store.put("vm", vm)
        runCurrent()
        val password = "BackupPassword123!".toCharArray()
        vm.exportBackup(password)

        // Lock after the export has passed the repository's initial session check.
        val deadline = System.nanoTime() + 40_000_000_000L
        while (!VaultAccess.mutations.isLocked) {
            check(System.nanoTime() < deadline) { "Timed out waiting for export mutation gate" }
            withContext(Dispatchers.IO) { delay(1) }
        }
        // Let the gate holder pass its key read and enter the 600k-iteration derivation.
        withContext(Dispatchers.IO) { delay(50) }
        vm.onActivityStopped(isChangingConfigurations = false)
        while (vm.uiState.value.isBackupBusy) {
            check(System.nanoTime() < deadline) { "Timed out waiting for export completion" }
            runCurrent()
            withContext(Dispatchers.IO) { delay(5) }
        }

        assertNull(vm.uiState.value.exportReadyFileName)
        assertFalse(vm.uiState.value.isBackupBusy)
        assertEquals("Vault locked before the backup was ready. Unlock and export again.",
            vm.uiState.value.userMessage)
        assertFalse(vm.uiState.value.isUnlocked)
        assertFalse(SessionManager.hasKey())
        assertTrue(password.all { it == '\u0000' })
        assertNull(VaultViewModel::class.java.getDeclaredField("pendingExport")
            .apply { isAccessible = true }.get(vm))
    }
}
