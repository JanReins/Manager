package com.janreins.vaultlock.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.janreins.vaultlock.crypto.SessionManager
import com.janreins.vaultlock.data.SecurityPreferences
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: VaultViewModel
    private lateinit var store: ViewModelStore
    private var elapsed = 1_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val application = ApplicationProvider.getApplicationContext<Application>()
        val prefs = application.getSharedPreferences("lock_lifecycle_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SessionManager.setKey(SecretKeySpec(ByteArray(32) { 1 }, "AES"))
        viewModel = VaultViewModel(application, SecurityPreferences(application, customPrefs = prefs),
            dispatcher, elapsedRealtime = { elapsed })
        store = ViewModelStore().apply { put("vault", viewModel) }
    }

    @After
    fun tearDown() {
        store.clear()
        SessionManager.lock()
        Dispatchers.resetMain()
    }

    @Test
    fun `picker suppression consumes one stop with a 120 second timeout`() = runTest {
        runCurrent()
        viewModel.setAutoLockDuration(120)
        viewModel.suppressNextBackgroundLock()
        viewModel.onActivityStopped(false)
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertTrue(SessionManager.hasKey())
        viewModel.onActivityStopped(false)
        assertFalse(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun `immediate background lock cannot be suppressed`() = runTest {
        runCurrent()
        viewModel.setAutoLockDuration(0)
        viewModel.suppressNextBackgroundLock()
        viewModel.onActivityStopped(false)
        assertFalse(viewModel.uiState.value.isUnlocked)
        assertFalse(SessionManager.hasKey())
    }

    @Test
    fun `switching to immediate lock invalidates an armed suppression`() = runTest {
        runCurrent()
        viewModel.suppressNextBackgroundLock()
        viewModel.setAutoLockDuration(0)
        viewModel.onActivityStopped(false)
        assertFalse(SessionManager.hasKey())
    }

    @Test
    fun `cancelling suppression after launch failure restores next stop lock`() = runTest {
        runCurrent()
        viewModel.suppressNextBackgroundLock()
        viewModel.cancelSuppressedBackgroundLock()
        viewModel.onActivityStopped(false)
        assertFalse(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun `stale picker suppression does not consume a stop`() = runTest {
        runCurrent()
        viewModel.suppressNextBackgroundLock()
        elapsed += VaultViewModel.PICKER_SUPPRESS_WINDOW_MS + 1
        viewModel.onActivityStopped(false)
        assertFalse(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun `rotate then foreground then Home locks normally`() = runTest {
        runCurrent()
        viewModel.onActivityStopped(true)
        runCurrent()
        advanceTimeBy(1_000)
        assertTrue(viewModel.uiState.value.isUnlocked)
        viewModel.onAppForegrounded()
        viewModel.onActivityStopped(false)
        assertFalse(viewModel.uiState.value.isUnlocked)
        assertFalse(SessionManager.hasKey())
    }

    @Test
    fun `configuration change without foreground locks at five seconds`() = runTest {
        runCurrent()
        viewModel.onActivityStopped(true)
        runCurrent()
        advanceTimeBy(VaultViewModel.CONFIG_CHANGE_GRACE_MS - 1)
        assertTrue(viewModel.uiState.value.isUnlocked)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(viewModel.uiState.value.isUnlocked)
        assertFalse(SessionManager.hasKey())
    }

    @Test
    fun `foreground cancels configuration guard`() = runTest {
        runCurrent()
        viewModel.onActivityStopped(true)
        runCurrent()
        viewModel.onAppForegrounded()
        advanceTimeBy(VaultViewModel.CONFIG_CHANGE_GRACE_MS)
        runCurrent()
        assertTrue(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun `draft survives recreation and all drafts clear on lock`() = runTest {
        runCurrent()
        val draft = viewModel.entryDraft("back-stack-id")
        draft.title = "Edited title"
        draft.username = "user"
        draft.password = "secret"
        draft.url = "offline.example"
        draft.notes = "unsaved notes"
        draft.totpSecret = "JBSWY3DPEHPK3PXP"
        draft.category = "Secure Note"
        draft.isFavorite = true
        draft.createdAt = 123L
        draft.loadedEntryId = 7L
        val another = viewModel.entryDraft("another-entry").apply { password = "another secret" }

        viewModel.onActivityStopped(true)
        runCurrent()
        viewModel.onAppForegrounded()
        assertSame(draft, viewModel.entryDraft("back-stack-id"))
        assertEquals("secret", draft.password)
        assertEquals("unsaved notes", draft.notes)
        assertEquals(7L, checkNotNull(draft.loadedEntryId))
        assertEquals(123L, draft.createdAt)
        assertTrue(draft.isFavorite)

        viewModel.lockVault()
        assertEquals("", draft.title)
        assertEquals("", draft.username)
        assertEquals("", draft.password)
        assertEquals("", draft.url)
        assertEquals("", draft.notes)
        assertEquals("", draft.totpSecret)
        assertNull(draft.loadedEntryId)
        assertEquals("", another.password)
        assertNotSame(draft, viewModel.entryDraft("back-stack-id"))
    }

    @Test
    fun `explicit draft discard removes and clears only that draft`() = runTest {
        runCurrent()
        val discarded = viewModel.entryDraft("discard").apply { password = "secret" }
        val retained = viewModel.entryDraft("keep").apply { title = "keep" }
        viewModel.discardEntryDraft("discard")
        assertEquals("", discarded.password)
        assertNotSame(discarded, viewModel.entryDraft("discard"))
        assertSame(retained, viewModel.entryDraft("keep"))
    }
}
