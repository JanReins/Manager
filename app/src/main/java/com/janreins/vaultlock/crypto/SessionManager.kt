package com.janreins.vaultlock.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey

/**
 * Manages the in-memory active cryptographic session.
 * Best-effort zeroization on lock to clear key references and destroy secret key objects where supported.
 * Note: Due to JVM heap behavior, immutable SecretKeySpec internal arrays may persist until collected by GC.
 */
object SessionManager {
    private var activeSecretKey: SecretKey? = null
    private var lastActiveTimestamp: Long = System.currentTimeMillis()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun setKey(key: SecretKey) {
        activeSecretKey = key
        lastActiveTimestamp = System.currentTimeMillis()
        _isUnlocked.value = true
    }

    fun getKey(): SecretKey {
        return activeSecretKey ?: throw IllegalStateException("Vault is locked")
    }

    fun hasKey(): Boolean = activeSecretKey != null

    fun recordActivity() {
        lastActiveTimestamp = System.currentTimeMillis()
    }

    fun getLastActivity(): Long = lastActiveTimestamp

    fun lock() {
        val key = activeSecretKey
        if (key != null) {
            try {
                if (!key.isDestroyed) {
                    key.destroy()
                }
            } catch (_: Exception) {
                // Best effort destroy if supported by provider/implementation
            }
        }
        activeSecretKey = null
        _isUnlocked.value = false
    }
}
