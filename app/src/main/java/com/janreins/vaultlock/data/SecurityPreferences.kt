package com.janreins.vaultlock.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.janreins.vaultlock.crypto.BiometricHelper
import com.janreins.vaultlock.crypto.CryptoManager
import javax.crypto.SecretKey

class SecurityPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vaultlock_security_prefs", Context.MODE_PRIVATE)

    companion object {
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
     * Initializes the Master Password for the first time.
     * Generates a unique 32-byte salt, derives the Master Key via PBKDF2,
     * encrypts the magic verification token, and optionally enables biometric.
     */
    fun setupMasterPassword(password: CharArray, enableBiometric: Boolean = false): SecretKey {
        val salt = CryptoManager.generateSalt()
        val derivedKey = CryptoManager.deriveKey(password, salt)
        val verifierEncrypted = CryptoManager.encrypt(VERIFIER_MAGIC, derivedKey)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

        val editor = prefs.edit()
            .putBoolean(KEY_IS_SETUP, true)
            .putString(KEY_SALT, saltBase64)
            .putString(KEY_VERIFIER, verifierEncrypted)
            .putLong(KEY_AUTO_LOCK_SECONDS, 120L)

        if (enableBiometric) {
            try {
                val wrapped = BiometricHelper.wrapMasterKey(derivedKey)
                editor.putBoolean(KEY_BIOMETRIC_ENABLED, true)
                editor.putString(KEY_WRAPPED_KEY, wrapped)
            } catch (e: Exception) {
                editor.putBoolean(KEY_BIOMETRIC_ENABLED, false)
            }
        } else {
            editor.putBoolean(KEY_BIOMETRIC_ENABLED, false)
            editor.remove(KEY_WRAPPED_KEY)
        }

        editor.apply()
        return derivedKey
    }

    /**
     * Verifies the user entered Master Password.
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
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Attempts to unlock using Keystore-wrapped biometric token.
     */
    fun unlockWithBiometric(): SecretKey? {
        if (!isBiometricEnabled) return null
        val wrappedKey = prefs.getString(KEY_WRAPPED_KEY, null) ?: return null
        return try {
            val unwrappedKey = BiometricHelper.unwrapMasterKey(wrappedKey)
            val verifierEncrypted = prefs.getString(KEY_VERIFIER, null) ?: return null
            val decrypted = CryptoManager.decrypt(verifierEncrypted, unwrappedKey)
            if (decrypted == VERIFIER_MAGIC) {
                unwrappedKey
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun setBiometricEnabled(enabled: Boolean, currentKey: SecretKey?): Boolean {
        return if (enabled) {
            if (currentKey == null) {
                false
            } else {
                try {
                    val wrapped = BiometricHelper.wrapMasterKey(currentKey)
                    prefs.edit()
                        .putBoolean(KEY_BIOMETRIC_ENABLED, true)
                        .putString(KEY_WRAPPED_KEY, wrapped)
                        .apply()
                    true
                } catch (e: Exception) {
                    false
                }
            }
        } else {
            prefs.edit()
                .putBoolean(KEY_BIOMETRIC_ENABLED, false)
                .remove(KEY_WRAPPED_KEY)
                .apply()
            true
        }
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
