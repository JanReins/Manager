package com.janreins.vaultlock.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.vaultlock.crypto.BiometricHelper
import com.janreins.vaultlock.crypto.CryptoManager
import com.janreins.vaultlock.crypto.SessionManager
import com.janreins.vaultlock.data.SecurityPreferences
import com.janreins.vaultlock.data.VaultDatabase
import com.janreins.vaultlock.data.VaultEntry
import com.janreins.vaultlock.data.VaultRepository
import com.janreins.vaultlock.generator.GeneratorOptions
import com.janreins.vaultlock.generator.PasswordGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.crypto.SecretKey

data class VaultUiState(
    val isMasterPasswordSet: Boolean = false,
    val isUnlocked: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val autoLockSeconds: Long = 120L,
    val themeMode: String = "dark",
    val searchQuery: String = "",
    val selectedCategory: String = "All", // All, Favorites, Logins, Cards, Notes, Secure
    val allEntries: List<VaultEntry> = emptyList(),
    val filteredEntries: List<VaultEntry> = emptyList(),
    val currentGeneratedPassword: String = "",
    val generatorOptions: GeneratorOptions = GeneratorOptions(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val activeCopiedLabel: String? = null, // For clipboard feedback animation
    val lockoutRemainingSeconds: Long = 0L,
    val userMessage: String? = null // One-shot, non-sensitive feedback shown as a toast
)

class VaultViewModel @JvmOverloads constructor(
    application: Application,
    customSecurityPreferences: SecurityPreferences? = null,
    /** Dispatcher for CPU-heavy PBKDF2 key derivation; injectable for tests. */
    private val kdfDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AndroidViewModel(application) {

    private val securityPreferences = customSecurityPreferences ?: SecurityPreferences(application)
    private val database = VaultDatabase.getInstance(application)
    private val repository = VaultRepository(database.vaultDao(), securityPreferences)

    private val _uiState = MutableStateFlow(
        VaultUiState(
            isMasterPasswordSet = securityPreferences.isMasterPasswordSet,
            isUnlocked = SessionManager.isUnlocked.value,
            isBiometricAvailable = BiometricHelper.isBiometricAvailable(application),
            isBiometricEnabled = securityPreferences.isBiometricEnabled,
            autoLockSeconds = securityPreferences.autoLockSeconds,
            themeMode = securityPreferences.themeMode,
            currentGeneratedPassword = PasswordGenerator.generate(GeneratorOptions())
        )
    )
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var autoLockJob: Job? = null
    private var lockoutCountdownJob: Job? = null
    private val clipboardClearHandler = Handler(Looper.getMainLooper())
    private var clipboardClearRunnable: Runnable? = null
    private var ownedClipboard: ClipboardManager? = null
    private var ownedClipLabel: String? = null
    private var ownedClipTimestamp: Long? = null

    init {
        // Check for existing unlock lockout on initialization
        if (securityPreferences.getLockoutRemainingMillis() > 0) {
            startLockoutCountdown()
        }

        // Observe SessionManager unlock state
        viewModelScope.launch {
            SessionManager.isUnlocked.collect { unlocked ->
                _uiState.update { if (unlocked) it.copy(isUnlocked = true) else it.clearedForLock() }
                if (unlocked) {
                    startInactivityTimer()
                } else {
                    stopInactivityTimer()
                }
            }
        }

        // Observe repository database entries and apply search + category filters
        viewModelScope.launch {
            repository.getAllEntries().collect { entries ->
                _uiState.update { current ->
                    val visibleEntries = if (SessionManager.hasKey()) entries else emptyList()
                    current.copy(
                        allEntries = visibleEntries,
                        filteredEntries = filterEntries(visibleEntries, current.searchQuery, current.selectedCategory)
                    )
                }
            }
        }
    }

    private fun filterEntries(
        entries: List<VaultEntry>,
        query: String,
        category: String
    ): List<VaultEntry> {
        return entries.filter { entry ->
            val matchesCategory = when (category) {
                "All" -> true
                "Favorites" -> entry.isFavorite
                "Login", "Logins" -> entry.category == "Login"
                "Card", "Cards" -> entry.category == "Card"
                "Secure Note", "Notes" -> entry.category == "Secure Note"
                else -> entry.category.equals(category, ignoreCase = true)
            }

            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                entry.title.contains(query, ignoreCase = true) ||
                        entry.username.contains(query, ignoreCase = true) ||
                        entry.url.contains(query, ignoreCase = true) ||
                        entry.notes.contains(query, ignoreCase = true)
            }

            matchesCategory && matchesQuery
        }
    }

    fun onUserActivity() {
        if (_uiState.value.isUnlocked) {
            SessionManager.recordActivity()
        }
    }

    private fun startInactivityTimer() {
        autoLockJob?.cancel()
        autoLockJob = viewModelScope.launch {
            while (true) {
                delay(5000) // Check every 5 seconds
                val timeoutMillis = _uiState.value.autoLockSeconds * 1000L
                if (timeoutMillis > 0) {
                    val idle = System.currentTimeMillis() - SessionManager.getLastActivity()
                    if (idle >= timeoutMillis) {
                        lockVault()
                        break
                    }
                }
            }
        }
    }

    private fun stopInactivityTimer() {
        autoLockJob?.cancel()
        autoLockJob = null
    }

    private fun startLockoutCountdown() {
        lockoutCountdownJob?.cancel()
        lockoutCountdownJob = viewModelScope.launch {
            while (true) {
                val remainingMs = securityPreferences.getLockoutRemainingMillis()
                val remainingSec = (remainingMs + 999) / 1000
                if (remainingSec <= 0) {
                    _uiState.update { it.copy(lockoutRemainingSeconds = 0) }
                    break
                } else {
                    _uiState.update { it.copy(lockoutRemainingSeconds = remainingSec) }
                }
                delay(1000)
            }
        }
    }

    private var ignoreNextBackgroundLock = false

    fun suppressNextBackgroundLock() {
        ignoreNextBackgroundLock = true
    }

    /**
     * Locks the vault immediately when the application is sent to the background,
     * unless temporarily suppressed by system file picker or share sheet activities.
     */
    fun onAppBackgrounded() {
        if (ignoreNextBackgroundLock) {
            ignoreNextBackgroundLock = false
            return
        }
        lockVault(clearClipboard = false)
    }

    fun onAppForegrounded() {
        ignoreNextBackgroundLock = false
        if (_uiState.value.isUnlocked) {
            val timeoutMillis = _uiState.value.autoLockSeconds * 1000L
            if (timeoutMillis > 0) {
                val idle = System.currentTimeMillis() - SessionManager.getLastActivity()
                if (idle >= timeoutMillis) {
                    lockVault()
                }
            }
        }
    }

    /**
     * Initializes the Master Password for the first time.
     */
    fun setupMasterPassword(
        password: String,
        enableBiometric: Boolean,
        activity: FragmentActivity? = null,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (password.length < 8) {
            onComplete(false, "Password must be at least 8 characters long")
            return
        }
        viewModelScope.launch {
            try {
                val derivedKey = withContext(kdfDispatcher) { securityPreferences.setupMasterPassword(password.toCharArray()) }
                SessionManager.setKey(derivedKey)
                _uiState.update {
                    it.copy(
                        isMasterPasswordSet = true,
                        isUnlocked = true
                    )
                }

                if (enableBiometric && activity != null && BiometricHelper.isBiometricAvailable(activity)) {
                    BiometricHelper.promptBiometricEnrollment(
                        activity = activity,
                        masterKeyToWrap = derivedKey,
                        onEnrolled = { wrappedKey ->
                            securityPreferences.saveBiometricWrappedKey(wrappedKey)
                            _uiState.update { it.copy(isBiometricEnabled = true) }
                            onComplete(true, "Master Password created and biometric unlock registered")
                        },
                        onError = { error ->
                            securityPreferences.disableBiometric()
                            _uiState.update { it.copy(isBiometricEnabled = false) }
                            onComplete(true, "Master Password created (Biometric registration skipped: $error)")
                        }
                    )
                } else {
                    securityPreferences.disableBiometric()
                    _uiState.update { it.copy(isBiometricEnabled = false) }
                    onComplete(true, "Master Password created successfully")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(false, "Setup failed: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Unlocks the vault by verifying the master password and deriving the session key.
     * Enforces rate limiting with exponential backoff on failed attempts.
     */
    fun unlockWithPassword(password: String, onResult: (Boolean, String) -> Unit) {
        val remainingMs = securityPreferences.getLockoutRemainingMillis()
        if (remainingMs > 0) {
            val seconds = (remainingMs + 999) / 1000
            val msg = "Too many failed attempts. Try again in $seconds second(s)."
            _uiState.update { it.copy(errorMessage = msg, lockoutRemainingSeconds = seconds) }
            startLockoutCountdown()
            onResult(false, msg)
            return
        }

        viewModelScope.launch {
            try {
                var key = withContext(kdfDispatcher) { securityPreferences.verifyAndDeriveKey(password.toCharArray()) }
                if (securityPreferences.hasPendingMasterPasswordChange()) {
                    // Recover an interrupted master password change. Rows are re-encrypted in a single
                    // transaction, so the key that decrypts the vault tells which credentials are live.
                    val currentKey = key
                    if (currentKey != null && repository.keyDecryptsVault(currentKey) != false) {
                        if (!securityPreferences.clearPendingMasterPasswordChange()) {
                            throw IllegalStateException("Failed to clear pending master password change. Please retry.")
                        }
                    } else {
                        // Also covers a change to the same password text (new salt, new key).
                        val pendingKey = withContext(kdfDispatcher) { securityPreferences.verifyPendingAndDeriveKey(password.toCharArray()) }
                        if (pendingKey != null && repository.keyDecryptsVault(pendingKey) != false) {
                            if (!securityPreferences.promotePendingMasterPasswordChange()) {
                                throw IllegalStateException("Failed to finish pending master password change. Please retry with your NEW master password.")
                            }
                            key = pendingKey
                        } else if (pendingKey != null) {
                            if (!securityPreferences.clearPendingMasterPasswordChange()) {
                                throw IllegalStateException("Failed to clear pending master password change. Please retry.")
                            }
                            val msg = "The master password change did not complete. Unlock with your PREVIOUS master password."
                            _uiState.update { it.copy(errorMessage = msg) }
                            onResult(false, msg)
                            return@launch
                        } else if (currentKey != null) {
                            val msg = "A master password change was interrupted. Unlock with your NEW master password to finish it."
                            _uiState.update { it.copy(errorMessage = msg) }
                            onResult(false, msg)
                            return@launch
                        }
                    }
                }
                if (key != null) {
                    securityPreferences.resetFailedUnlockAttempts()
                    SessionManager.setKey(key)
                    _uiState.update { it.copy(isUnlocked = true, errorMessage = null, lockoutRemainingSeconds = 0) }
                    onResult(true, "Vault Unlocked")
                } else {
                    val delaySeconds = securityPreferences.recordFailedUnlockAttempt()
                    val msg = "Incorrect Master Password. Try again in $delaySeconds second(s)."
                    _uiState.update { it.copy(errorMessage = msg, lockoutRemainingSeconds = delaySeconds) }
                    startLockoutCountdown()
                    onResult(false, msg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(false, "Authentication error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Unlocks the vault using hardware-authenticated biometric authentication.
     * The master key is unwrapped atomically inside the CryptoObject callback.
     */
    fun unlockWithBiometric(activity: FragmentActivity, onResult: (Boolean, String) -> Unit) {
        if (securityPreferences.hasPendingMasterPasswordChange()) {
            onResult(false, "A master password change was interrupted. Unlock with your master password to recover it.")
            return
        }
        if (!_uiState.value.isBiometricEnabled) {
            onResult(false, "Biometric unlock not enabled")
            return
        }

        val wrappedKey = securityPreferences.getWrappedMasterKey()
        if (wrappedKey == null) {
            securityPreferences.disableBiometric()
            BiometricHelper.deleteKeystoreKey()
            _uiState.update { it.copy(isBiometricEnabled = false) }
            onResult(false, "Biometric credentials missing. Please unlock with master password.")
            return
        }

        BiometricHelper.promptBiometricUnlock(
            activity = activity,
            wrappedKeyBase64 = wrappedKey,
            onInvalidated = {
                securityPreferences.disableBiometric()
                _uiState.update { it.copy(isBiometricEnabled = false) }
            },
            onSuccess = { secretKey ->
                if (securityPreferences.hasPendingMasterPasswordChange()) {
                    onResult(false, "A master password change was interrupted. Unlock with your master password to recover it.")
                } else if (!securityPreferences.verifyKey(secretKey)) {
                    securityPreferences.disableBiometric()
                    val msg = "Biometric key is out of date. Unlock with your master password and re-enable biometric unlock."
                    _uiState.update { it.copy(isBiometricEnabled = false, errorMessage = msg) }
                    onResult(false, msg)
                } else {
                    securityPreferences.resetFailedUnlockAttempts()
                    SessionManager.setKey(secretKey)
                    _uiState.update { it.copy(isUnlocked = true, errorMessage = null, lockoutRemainingSeconds = 0) }
                    onResult(true, "Unlocked via Biometrics")
                }
            },
            onError = { error ->
                if (error != "cancelled") {
                    onResult(false, error)
                }
            }
        )
    }

    /**
     * Explicitly locks the vault session and wipes key material from memory.
     */
    fun lockVault(clearClipboard: Boolean = true) {
        // Backgrounding must not wipe a just-copied secret before the user can paste it elsewhere;
        // the 30-second timer still clears it in that case.
        if (clearClipboard) clearOwnedClipboard()
        SessionManager.lock()
        _uiState.update { it.clearedForLock() }
    }

    private fun VaultUiState.clearedForLock() = copy(
        isUnlocked = false,
        allEntries = emptyList(),
        filteredEntries = emptyList(),
        searchQuery = "",
        currentGeneratedPassword = "",
        activeCopiedLabel = null
    )

    fun consumeUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredEntries = filterEntries(current.allEntries, query, current.selectedCategory)
            )
        }
    }

    fun setSelectedCategory(category: String) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                filteredEntries = filterEntries(current.allEntries, current.searchQuery, category)
            )
        }
    }

    fun saveEntry(entry: VaultEntry, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.saveEntry(entry)
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Save failed: ${e.localizedMessage}") }
            }
        }
    }

    fun toggleFavorite(entry: VaultEntry) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(entry.id, !entry.isFavorite)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Favorite update failed: ${e.localizedMessage}") }
            }
        }
    }

    fun deleteEntry(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteEntry(id)
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Delete failed: ${e.localizedMessage}") }
            }
        }
    }

    fun changeMasterPassword(
        currentPass: String,
        newPass: String,
        confirmPass: String,
        activity: FragmentActivity? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        if (newPass != confirmPass) {
            onResult(false, "New passwords do not match")
            return
        }
        if (newPass.length < 8) {
            onResult(false, "New password must be at least 8 characters")
            return
        }

        viewModelScope.launch {
            try {
                val remainingMs = securityPreferences.getLockoutRemainingMillis()
                if (remainingMs > 0) {
                    val seconds = (remainingMs + 999) / 1000
                    val msg = "Too many failed attempts. Try again in $seconds second(s)."
                    _uiState.update { it.copy(errorMessage = msg, lockoutRemainingSeconds = seconds) }
                    startLockoutCountdown()
                    onResult(false, msg)
                    return@launch
                }

                val oldKey = withContext(kdfDispatcher) { securityPreferences.verifyAndDeriveKey(currentPass.toCharArray()) }
                if (oldKey == null) {
                    val delaySeconds = securityPreferences.recordFailedUnlockAttempt()
                    val msg = "Current password is incorrect. Try again in $delaySeconds second(s)."
                    _uiState.update { it.copy(errorMessage = msg, lockoutRemainingSeconds = delaySeconds) }
                    startLockoutCountdown()
                    onResult(false, msg)
                    return@launch
                }
                securityPreferences.resetFailedUnlockAttempts()

                val prepared = withContext(kdfDispatcher) { securityPreferences.prepareMasterPasswordChange(newPass.toCharArray()) }
                // Check after derivation too: another rotation may have staged while it ran.
                if (securityPreferences.hasPendingMasterPasswordChange()) {
                    onResult(false, "Unlock with your master password to finish the interrupted change before changing it again.")
                    return@launch
                }
                if (!securityPreferences.stagePendingMasterPasswordChange(prepared)) {
                    throw IllegalStateException("Failed to stage new Master Password; vault was not changed")
                }
                try {
                    repository.reEncryptAll(oldKey, prepared.secretKey)
                } catch (e: CancellationException) {
                    // IO may have committed before cancellation was delivered. Keep recovery metadata.
                    throw e
                } catch (e: Exception) {
                    securityPreferences.clearPendingMasterPasswordChange()
                    throw e
                }
                // The database now needs this key even if the preference commit fails.
                SessionManager.setKey(prepared.secretKey)
                val committed = securityPreferences.commitMasterPasswordChange(prepared)

                // Purge the old wrapped key before attempting enrollment with the new key.
                val wasBiometricEnabled = securityPreferences.isBiometricEnabled
                if (wasBiometricEnabled) {
                    securityPreferences.disableBiometric()
                    _uiState.update { it.copy(isBiometricEnabled = false) }
                }
                if (!committed) {
                    onResult(false, "Vault re-encrypted, but failed to persist new Master Password. Your NEW master password is required to finish recovery on the next unlock.")
                    return@launch
                }
                if (wasBiometricEnabled && activity != null) {
                    BiometricHelper.promptBiometricEnrollment(
                        activity = activity,
                        masterKeyToWrap = prepared.secretKey,
                        onEnrolled = { wrappedKey ->
                            securityPreferences.saveBiometricWrappedKey(wrappedKey)
                            _uiState.update { it.copy(isBiometricEnabled = true) }
                            onResult(true, "Master Password changed & biometric updated")
                        },
                        onError = {
                            securityPreferences.disableBiometric()
                            _uiState.update { it.copy(isBiometricEnabled = false) }
                            onResult(true, "Master Password changed (Biometric reset required)")
                        }
                    )
                } else {
                    onResult(true, if (wasBiometricEnabled) {
                        "Master Password changed (re-enable biometric unlock in Settings)"
                    } else "Master Password changed & vault re-encrypted")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(false, "Failed to change password: ${e.message}")
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean, activity: FragmentActivity?, onResult: (Boolean, String) -> Unit) {
        if (enabled) {
            val currentKey = if (SessionManager.hasKey()) SessionManager.getKey() else null
            if (currentKey == null || activity == null) {
                onResult(false, "Active unlocked session required to enable biometrics")
                return
            }
            BiometricHelper.promptBiometricEnrollment(
                activity = activity,
                masterKeyToWrap = currentKey,
                onEnrolled = { wrappedKey ->
                    securityPreferences.saveBiometricWrappedKey(wrappedKey)
                    _uiState.update { it.copy(isBiometricEnabled = true) }
                    onResult(true, "Biometric unlock enabled")
                },
                onError = { error ->
                    onResult(false, error)
                }
            )
        } else {
            securityPreferences.disableBiometric()
            BiometricHelper.deleteKeystoreKey()
            _uiState.update { it.copy(isBiometricEnabled = false) }
            onResult(true, "Biometric unlock disabled")
        }
    }

    fun setAutoLockDuration(seconds: Long) {
        securityPreferences.setAutoLockSeconds(seconds)
        _uiState.update { it.copy(autoLockSeconds = seconds) }
    }

    fun setTheme(themeMode: String) {
        securityPreferences.setThemeMode(themeMode)
        _uiState.update { it.copy(themeMode = themeMode) }
    }

    fun updateGeneratorOptions(options: GeneratorOptions) {
        val newPassword = PasswordGenerator.generate(options)
        _uiState.update {
            it.copy(
                generatorOptions = options,
                currentGeneratedPassword = newPassword
            )
        }
    }

    fun regeneratePassword() {
        val newPassword = PasswordGenerator.generate(_uiState.value.generatorOptions)
        _uiState.update { it.copy(currentGeneratedPassword = newPassword) }
    }

    /**
     * Copies sensitive text to clipboard with sensitive masking flag and 30s auto-clear.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "VaultLock Data") {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            // A fresh marker identifies this copy without retaining or comparing its plaintext.
            val marker = "VaultLock:${UUID.randomUUID()}"
            val clip = ClipData.newPlainText(marker, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
            clipboardClearRunnable?.let { clipboardClearHandler.removeCallbacks(it) }
            ownedClipboard = clipboard
            ownedClipLabel = marker
            ownedClipTimestamp = try {
                val description = clipboard.primaryClipDescription
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && description != null && description.label?.toString() == marker) {
                    description.timestamp
                } else null
            } catch (_: Exception) {
                null // The unique marker still allows ownership checks if timestamps are unavailable.
            }
            _uiState.update { it.copy(activeCopiedLabel = label) }
            val runnable = Runnable { clearOwnedClipboard() }
            clipboardClearRunnable = runnable
            clipboardClearHandler.postDelayed(runnable, 30_000)

            // Reset label animation in UI after 2.5 seconds.
            viewModelScope.launch {
                delay(2500)
                if (_uiState.value.activeCopiedLabel == label) {
                    _uiState.update { it.copy(activeCopiedLabel = null) }
                }
            }
        } catch (_: Exception) {
            // Clipboard access may be denied while the app is in the background.
        }
    }

    override fun onCleared() {
        clearOwnedClipboard()
        super.onCleared()
    }

    private fun clearOwnedClipboard() {
        clipboardClearRunnable?.let { clipboardClearHandler.removeCallbacks(it) }
        clipboardClearRunnable = null
        try {
            val clipboard = ownedClipboard
            val marker = ownedClipLabel
            val description = clipboard?.primaryClipDescription
            val timestamp = ownedClipTimestamp
            // Android 10+ hides the clipboard from background apps (description == null). Clear anyway
            // then, because a stale copied secret is worse than dropping a newer clip.
            val stillOurs = description == null || (description.label?.toString() == marker &&
                (timestamp == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && description.timestamp == timestamp)))
            if (clipboard != null && marker != null && stillOurs) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        } catch (_: Exception) {
            // Clipboard access may be denied while the app is in the background.
        } finally {
            ownedClipboard = null
            ownedClipLabel = null
            ownedClipTimestamp = null
            _uiState.update { it.copy(activeCopiedLabel = null) }
        }
    }

    fun exportBackup(onReady: (ByteArray?) -> Unit) {
        _uiState.update { it.copy(errorMessage = null) }
        viewModelScope.launch {
            try {
                val payload = repository.createEncryptedBackupPayload()
                onReady(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Backup failed: ${e.localizedMessage}") }
                onReady(null)
            }
        }
    }

    fun importBackup(encryptedBytes: ByteArray, onComplete: (Boolean, Int, String) -> Unit) {
        viewModelScope.launch {
            try {
                val restoredCount = repository.restoreEncryptedBackupPayload(encryptedBytes)
                onComplete(true, restoredCount, "Successfully restored $restoredCount entries")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onComplete(false, 0, "Failed to restore backup: Invalid password or corrupted file")
            }
        }
    }

    fun wipeAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.wipeEverything()
                BiometricHelper.deleteKeystoreKey()
                _uiState.update {
                    VaultUiState(
                        isMasterPasswordSet = false,
                        isUnlocked = false,
                        isBiometricAvailable = BiometricHelper.isBiometricAvailable(getApplication()),
                        isBiometricEnabled = false
                    )
                }
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Wipe failed: ${e.localizedMessage}") }
            }
        }
    }
}
