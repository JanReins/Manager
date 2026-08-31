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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val activeCopiedLabel: String? = null // For clipboard feedback animation
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val securityPreferences = SecurityPreferences(application)
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
    private val clipboardClearHandler = Handler(Looper.getMainLooper())
    private var clipboardClearRunnable: Runnable? = null

    init {
        // Observe SessionManager unlock state
        viewModelScope.launch {
            SessionManager.isUnlocked.collect { unlocked ->
                _uiState.update { it.copy(isUnlocked = unlocked) }
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
                    current.copy(
                        allEntries = entries,
                        filteredEntries = filterEntries(entries, current.searchQuery, current.selectedCategory)
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

    /**
     * Locks the vault immediately when the application is sent to the background.
     */
    fun onAppBackgrounded() {
        lockVault()
    }

    fun onAppForegrounded() {
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
                val derivedKey = securityPreferences.setupMasterPassword(password.toCharArray())
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
            } catch (e: Exception) {
                onComplete(false, "Setup failed: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Unlocks the vault by verifying the master password and deriving the session key.
     */
    fun unlockWithPassword(password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val key = securityPreferences.verifyAndDeriveKey(password.toCharArray())
                if (key != null) {
                    SessionManager.setKey(key)
                    _uiState.update { it.copy(isUnlocked = true, errorMessage = null) }
                    onResult(true, "Vault Unlocked")
                } else {
                    onResult(false, "Incorrect Master Password")
                }
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
        if (!_uiState.value.isBiometricEnabled) {
            onResult(false, "Biometric unlock not enabled")
            return
        }

        val wrappedKey = securityPreferences.getWrappedMasterKey()
        if (wrappedKey == null) {
            onResult(false, "Biometric credentials missing. Please unlock with master password.")
            return
        }

        BiometricHelper.promptBiometricUnlock(
            activity = activity,
            wrappedKeyBase64 = wrappedKey,
            onSuccess = { secretKey ->
                SessionManager.setKey(secretKey)
                _uiState.update { it.copy(isUnlocked = true, errorMessage = null) }
                onResult(true, "Unlocked via Biometrics")
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
    fun lockVault() {
        SessionManager.lock()
        _uiState.update { it.copy(isUnlocked = false) }
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
            repository.saveEntry(entry)
            onComplete()
        }
    }

    fun toggleFavorite(entry: VaultEntry) {
        viewModelScope.launch {
            repository.toggleFavorite(entry.id, !entry.isFavorite)
        }
    }

    fun deleteEntry(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteEntry(id)
            onComplete()
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
                val oldKey = securityPreferences.verifyAndDeriveKey(currentPass.toCharArray())
                if (oldKey == null) {
                    onResult(false, "Current password is incorrect")
                    return@launch
                }

                val newKey = securityPreferences.setupMasterPassword(newPass.toCharArray())
                repository.reEncryptAll(oldKey, newKey)
                SessionManager.setKey(newKey)

                // Re-enroll biometric if it was active
                if (securityPreferences.isBiometricEnabled && activity != null) {
                    BiometricHelper.promptBiometricEnrollment(
                        activity = activity,
                        masterKeyToWrap = newKey,
                        onEnrolled = { wrappedKey ->
                            securityPreferences.saveBiometricWrappedKey(wrappedKey)
                            onResult(true, "Master Password changed & biometric updated")
                        },
                        onError = {
                            securityPreferences.disableBiometric()
                            _uiState.update { it.copy(isBiometricEnabled = false) }
                            onResult(true, "Master Password changed (Biometric reset required)")
                        }
                    )
                } else {
                    onResult(true, "Master Password changed & vault re-encrypted")
                }
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
            val clip = ClipData.newPlainText(label, text)
            // Mark sensitive for Android 13+ clipboard preview masking
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)

            _uiState.update { it.copy(activeCopiedLabel = label) }

            // Auto-clear clipboard after 30 seconds for security
            clipboardClearRunnable?.let { clipboardClearHandler.removeCallbacks(it) }
            val runnable = Runnable {
                try {
                    val currentClip = clipboard.primaryClip
                    if (currentClip != null && currentClip.itemCount > 0 && currentClip.getItemAt(0).text == text) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } catch (_: Exception) {
                    // Ignore clipboard error
                }
                _uiState.update { it.copy(activeCopiedLabel = null) }
            }
            clipboardClearRunnable = runnable
            clipboardClearHandler.postDelayed(runnable, 30_000)

            // Reset label animation in UI after 2.5 seconds
            viewModelScope.launch {
                delay(2500)
                if (_uiState.value.activeCopiedLabel == label) {
                    _uiState.update { it.copy(activeCopiedLabel = null) }
                }
            }
        } catch (_: Exception) {
            // Fallback
        }
    }

    fun exportBackup(onReady: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            try {
                val payload = repository.createEncryptedBackupPayload()
                onReady(payload)
            } catch (_: Exception) {
                onReady(null)
            }
        }
    }

    fun importBackup(encryptedBytes: ByteArray, onComplete: (Boolean, Int, String) -> Unit) {
        viewModelScope.launch {
            try {
                val restoredCount = repository.restoreEncryptedBackupPayload(encryptedBytes)
                onComplete(true, restoredCount, "Successfully restored $restoredCount entries")
            } catch (_: Exception) {
                onComplete(false, 0, "Failed to restore backup: Invalid password or corrupted file")
            }
        }
    }

    fun wipeAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
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
        }
    }
}
