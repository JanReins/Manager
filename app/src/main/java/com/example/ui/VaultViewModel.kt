package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.BiometricHelper
import com.example.crypto.SessionManager
import com.example.data.SecurityPreferences
import com.example.data.VaultDatabase
import com.example.data.VaultEntry
import com.example.data.VaultRepository
import com.example.generator.GeneratorOptions
import com.example.generator.PasswordGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultUiState(
    val isSetup: Boolean = false,
    val isUnlocked: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val autoLockSeconds: Long = 120L,
    val themeMode: String = "dark",
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val unlockError: String? = null,
    val infoMessage: String? = null,
    val isGeneratingPassword: Boolean = false,
    val generatorOptions: GeneratorOptions = GeneratorOptions(),
    val currentGeneratedPassword: String = "",
    val activeCopiedLabel: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val securityPrefs = SecurityPreferences(application)
    private val database = VaultDatabase.getInstance(application)
    private val repository = VaultRepository(database.vaultDao(), securityPrefs)

    private val _uiState = MutableStateFlow(
        VaultUiState(
            isSetup = securityPrefs.isMasterPasswordSet,
            isUnlocked = SessionManager.isUnlocked.value,
            isBiometricEnabled = securityPrefs.isBiometricEnabled,
            isBiometricAvailable = BiometricHelper.isBiometricAvailable(application),
            autoLockSeconds = securityPrefs.autoLockSeconds,
            themeMode = securityPrefs.themeMode,
            currentGeneratedPassword = PasswordGenerator.generate(GeneratorOptions())
        )
    )
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    // Filtered entries stream
    val entries: StateFlow<List<VaultEntry>> = combine(
        repository.allEntries,
        _uiState
    ) { allEntries, state ->
        allEntries.filter { entry ->
            val matchesCategory = when (state.selectedCategory) {
                "All" -> true
                "Favorites" -> entry.isFavorite
                else -> entry.category.equals(state.selectedCategory, ignoreCase = true)
            }
            val matchesQuery = if (state.searchQuery.isBlank()) {
                true
            } else {
                val query = state.searchQuery.trim().lowercase()
                entry.title.lowercase().contains(query) ||
                        entry.username.lowercase().contains(query) ||
                        entry.url.lowercase().contains(query) ||
                        entry.notes.lowercase().contains(query)
            }
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var autoLockJob: Job? = null
    private var clipboardTimerJob: Job? = null

    init {
        startAutoLockMonitor()
    }

    private fun startAutoLockMonitor() {
        autoLockJob?.cancel()
        autoLockJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                if (SessionManager.hasKey()) {
                    val idleTime = System.currentTimeMillis() - SessionManager.getLastActivity()
                    val maxIdleMs = securityPrefs.autoLockSeconds * 1000L
                    if (securityPrefs.autoLockSeconds > 0 && idleTime >= maxIdleMs) {
                        lockVault()
                    }
                }
            }
        }
    }

    fun onUserActivity() {
        SessionManager.recordActivity()
    }

    fun onAppBackgrounded() {
        if (securityPrefs.autoLockSeconds == 0L) {
            lockVault()
        }
    }

    fun onAppForegrounded() {
        if (SessionManager.hasKey()) {
            val idleTime = System.currentTimeMillis() - SessionManager.getLastActivity()
            val maxIdleMs = securityPrefs.autoLockSeconds * 1000L
            if (securityPrefs.autoLockSeconds > 0 && idleTime >= maxIdleMs) {
                lockVault()
            }
        }
    }

    fun setupMasterPassword(
        password: String,
        confirm: String,
        enableBiometric: Boolean,
        onSuccess: () -> Unit
    ) {
        if (password.length < 8) {
            _uiState.value = _uiState.value.copy(unlockError = "Password must be at least 8 characters")
            return
        }
        if (password != confirm) {
            _uiState.value = _uiState.value.copy(unlockError = "Passwords do not match")
            return
        }

        try {
            val key = securityPrefs.setupMasterPassword(password.toCharArray(), enableBiometric)
            SessionManager.setKey(key)
            _uiState.value = _uiState.value.copy(
                isSetup = true,
                isUnlocked = true,
                isBiometricEnabled = securityPrefs.isBiometricEnabled,
                unlockError = null,
                infoMessage = "Vault initialized securely"
            )
            onSuccess()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(unlockError = "Failed to setup master key: ${e.message}")
        }
    }

    fun unlockWithPassword(password: String, onSuccess: () -> Unit) {
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(unlockError = "Please enter your master password")
            return
        }
        val key = securityPrefs.verifyAndDeriveKey(password.toCharArray())
        if (key != null) {
            SessionManager.setKey(key)
            _uiState.value = _uiState.value.copy(
                isUnlocked = true,
                unlockError = null,
                infoMessage = null
            )
            onSuccess()
        } else {
            _uiState.value = _uiState.value.copy(unlockError = "Incorrect Master Password")
        }
    }

    fun unlockWithBiometric(onSuccess: () -> Unit) {
        val key = securityPrefs.unlockWithBiometric()
        if (key != null) {
            SessionManager.setKey(key)
            _uiState.value = _uiState.value.copy(
                isUnlocked = true,
                unlockError = null
            )
            onSuccess()
        } else {
            _uiState.value = _uiState.value.copy(
                unlockError = "Biometric token expired or invalid. Please enter master password."
            )
        }
    }

    fun lockVault() {
        SessionManager.lock()
        _uiState.value = _uiState.value.copy(
            isUnlocked = false,
            unlockError = null,
            searchQuery = "",
            activeCopiedLabel = null
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        onUserActivity()
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        onUserActivity()
    }

    fun clearUnlockError() {
        _uiState.value = _uiState.value.copy(unlockError = null)
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun saveEntry(entry: VaultEntry, onDone: () -> Unit) {
        if (entry.title.isBlank()) {
            _uiState.value = _uiState.value.copy(infoMessage = "Title is required")
            return
        }
        viewModelScope.launch {
            try {
                repository.saveEntry(entry)
                _uiState.value = _uiState.value.copy(
                    infoMessage = if (entry.id == 0L) "Entry created securely" else "Entry updated"
                )
                onUserActivity()
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(infoMessage = "Error saving entry: ${e.message}")
            }
        }
    }

    fun deleteEntry(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteEntry(id)
                _uiState.value = _uiState.value.copy(infoMessage = "Entry deleted")
                onUserActivity()
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(infoMessage = "Error deleting: ${e.message}")
            }
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
            onUserActivity()
        }
    }

    suspend fun getEntry(id: Long): VaultEntry? {
        return repository.getEntryById(id)
    }

    fun changeMasterPassword(
        currentPass: String,
        newPass: String,
        confirmNew: String,
        onDone: (Boolean, String) -> Unit
    ) {
        if (newPass.length < 8) {
            onDone(false, "New password must be at least 8 characters")
            return
        }
        if (newPass != confirmNew) {
            onDone(false, "New passwords do not match")
            return
        }

        val oldKey = securityPrefs.verifyAndDeriveKey(currentPass.toCharArray())
        if (oldKey == null) {
            onDone(false, "Current password is incorrect")
            return
        }

        viewModelScope.launch {
            try {
                val newKey = securityPrefs.setupMasterPassword(
                    newPass.toCharArray(),
                    securityPrefs.isBiometricEnabled
                )
                repository.reEncryptAll(oldKey, newKey)
                SessionManager.setKey(newKey)
                _uiState.value = _uiState.value.copy(infoMessage = "Master Password updated successfully")
                onDone(true, "Master Password updated successfully")
            } catch (e: Exception) {
                onDone(false, "Failed to update password: ${e.message}")
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean, onDone: (Boolean) -> Unit) {
        if (!SessionManager.hasKey()) {
            onDone(false)
            return
        }
        val success = securityPrefs.setBiometricEnabled(enabled, SessionManager.getKey())
        _uiState.value = _uiState.value.copy(
            isBiometricEnabled = securityPrefs.isBiometricEnabled
        )
        onDone(success)
    }

    fun setAutoLockDuration(seconds: Long) {
        securityPrefs.setAutoLockSeconds(seconds)
        _uiState.value = _uiState.value.copy(autoLockSeconds = seconds)
    }

    fun setTheme(mode: String) {
        securityPrefs.setThemeMode(mode)
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun wipeAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.clearAll()
            _uiState.value = VaultUiState(
                isSetup = false,
                isUnlocked = false,
                isBiometricEnabled = false,
                isBiometricAvailable = BiometricHelper.isBiometricAvailable(getApplication()),
                currentGeneratedPassword = PasswordGenerator.generate(GeneratorOptions())
            )
            onDone()
        }
    }

    // Password Generator
    fun updateGeneratorOptions(options: GeneratorOptions) {
        val newPassword = PasswordGenerator.generate(options)
        _uiState.value = _uiState.value.copy(
            generatorOptions = options,
            currentGeneratedPassword = newPassword
        )
    }

    fun regeneratePassword() {
        val newPassword = PasswordGenerator.generate(_uiState.value.generatorOptions)
        _uiState.value = _uiState.value.copy(currentGeneratedPassword = newPassword)
    }

    // Clipboard handling
    fun copyToClipboard(context: Context, text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        _uiState.value = _uiState.value.copy(
            activeCopiedLabel = label,
            infoMessage = "$label copied to clipboard"
        )
        onUserActivity()

        // Auto clear feedback after 3 seconds
        clipboardTimerJob?.cancel()
        clipboardTimerJob = viewModelScope.launch {
            delay(3000)
            if (_uiState.value.activeCopiedLabel == label) {
                _uiState.value = _uiState.value.copy(activeCopiedLabel = null)
            }
        }
    }

    // Backup & Restore
    fun exportBackup(onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            try {
                val data = repository.exportEncryptedBackup()
                onResult(data)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(infoMessage = "Export failed: ${e.message}")
                onResult(null)
            }
        }
    }

    fun importBackup(bytes: ByteArray, onResult: (Boolean, Int, String) -> Unit) {
        viewModelScope.launch {
            try {
                val count = repository.importEncryptedBackup(bytes)
                _uiState.value = _uiState.value.copy(infoMessage = "Successfully imported $count entries")
                onResult(true, count, "Successfully restored $count items")
            } catch (e: Exception) {
                onResult(false, 0, "Failed to decrypt backup. Ensure backup belongs to this Master Password.")
            }
        }
    }
}
