package com.janreins.vaultlock.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.janreins.vaultlock.crypto.CryptoManager
import javax.crypto.SecretKey

/**
 * Security preferences stored in Android Keystore backed EncryptedSharedPreferences.
 * Stores encryption salt, authentication verifier token, wrapped master keys, and app flags.
 */
class SecurityPreferences(context: Context) {

    private val prefs: SharedPreferences

    init {
        // Initialize MasterKey for AES-256-GCM Keystore-backed encryption of preferences
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Seamless one-time migration from legacy plain SharedPreferences if present
        migrateLegacyPlainPrefsIfPresent(context)
    }

    private fun migrateLegacyPlainPrefsIfPresent(context: Context) {
        try {
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
            if (legacyPrefs.contains(KEY_IS_SETUP) && !prefs.contains(KEY_IS_SETUP)) {
                val editor = prefs.edit()
                legacyPrefs.all.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is String -> editor.putString(key, value)
                        is Long -> editor.putLong(key, value)
                        is Int -> editor.putInt(key, value)
                        is Float -> editor.putFloat(key, value)
                    }
                }
                editor.apply()
                // Clear plain legacy preferences file for security
                legacyPrefs.edit().clear().apply()
            }
        } catch (_: Exception) {
            // Ignore migration failure and proceed
        }
    }

    companion object {
        private const val ENCRYPTED_PREFS_FILE = "vaultlock_security_encrypted_prefs"
        private const val LEGACY_PREFS_FILE = "vaultlock_security_prefs"

        private const val KEY_IS_SETUP = "key_is_setup"
        private const val KEY_SALT = "key_master_salt"
        private const val KEY_VERIFIER = "key_auth_verifier"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_WRAPPED_KEY = "key_wrapped_master_key"
        private const val KEY_AUTO_LOCK_SECONDS = "key_auto_lock_seconds"
        private const val KEY_THEME_MODE = "key_theme_mode" // "system", "dark", "light"
        private const val VERIFIER_MAGIC = "VAULTLOCK_VERIFY_PAYLOAD_V1"
    }

    val isMasterPasswordSet: Boolean
        get() = prefs.getBoolean(KEY_IS_SETUP, false)

    val isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    val autoLockSeconds: Long
        get() = prefs.getLong(KEY_AUTO_LOCK_SECONDS, 120L) // Default 2 minutes

    val themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "dark") ?: "dark"

    /**
     * Initializes the Master Password for the first time or updates it.
     * Generates a unique 32-byte salt, derives the Master Key via PBKDF2 (150,000 iterations),
     * and stores the encrypted magic verification token in EncryptedSharedPreferences.
     */
    fun setupMasterPassword(password: CharArray): SecretKey {
        val salt = CryptoManager.generateSalt()
        val derivedKey = CryptoManager.deriveKey(password, salt)
        val verifierEncrypted = CryptoManager.encrypt(VERIFIER_MAGIC, derivedKey)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

        prefs.edit()
            .putBoolean(KEY_IS_SETUP, true)
            .putString(KEY_SALT, saltBase64)
            .putString(KEY_VERIFIER, verifierEncrypted)
            .putLong(KEY_AUTO_LOCK_SECONDS, prefs.getLong(KEY_AUTO_LOCK_SECONDS, 120L))
            .apply()

        return derivedKey
    }

    /**
     * Verifies the user entered Master Password against the stored verifier payload.
     * Returns the derived SecretKey if valid, or null if incorrect.
     */
    fun verifyAndDeriveKey(password: CharArray): SecretKey? {
        val saltBase64 = prefs.getString(KEY_SALT, null) ?: return null
        val verifierEncrypted = prefs.getString(KEY_VERIFIER, null) ?: return null

        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val derivedKey = CryptoManager.deriveKey(password, salt)

        return try {
            val decrypted = CryptoManager.decrypt(verifierEncrypted, derivedKey)
            if (decrypted == VERIFIER_MAGIC) {
                derivedKey
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the wrapped master key string if biometric unlock is enabled.
     */
    fun getWrappedMasterKey(): String? {
        if (!isBiometricEnabled) return null
        return prefs.getString(KEY_WRAPPED_KEY, null)
    }

    /**
     * Saves the Keystore-wrapped master key token.
     */
    fun saveBiometricWrappedKey(wrappedKeyBase64: String) {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, true)
            .putString(KEY_WRAPPED_KEY, wrappedKeyBase64)
            .apply()
    }

    /**
     * Disables biometric unlock and purges the stored wrapped key.
     */
    fun disableBiometric() {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .remove(KEY_WRAPPED_KEY)
            .apply()
    }

    fun setAutoLockSeconds(seconds: Long) {
        prefs.edit().putLong(KEY_AUTO_LOCK_SECONDS, seconds).apply()
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun wipeAll() {
        prefs.edit().clear().apply()
    }
}
