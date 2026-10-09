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
    private val _activeKey = MutableStateFlow<SecretKey?>(null)
    val activeKey: StateFlow<SecretKey?> = _activeKey.asStateFlow()
    private var lastActiveTimestamp: Long = System.currentTimeMillis()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // Changes on every lock/unlock, including a lock while authentication is still running.
    @Volatile private var generation: Long = 0
    fun generation(): Long = generation

    @Synchronized
    fun setKeyIfCurrent(key: SecretKey, expectedGeneration: Long): Boolean {
        if (generation != expectedGeneration) return false
        setKey(key)
        return true
    }

    @Synchronized
    fun setKey(key: SecretKey) {
        generation++
        _activeKey.value = key
        lastActiveTimestamp = System.currentTimeMillis()
        _isUnlocked.value = true
    }

    fun getKey(): SecretKey {
        return _activeKey.value ?: throw IllegalStateException("Vault is locked")
    }

    fun hasKey(): Boolean = _activeKey.value != null

    fun recordActivity() {
        lastActiveTimestamp = System.currentTimeMillis()
    }

    fun getLastActivity(): Long = lastActiveTimestamp

    @Synchronized
    fun lock() {
        generation++
        val key = _activeKey.value
        _activeKey.value = null
        if (key != null) {
            try {
                if (!key.isDestroyed) {
                    key.destroy()
                }
            } catch (_: Exception) {
                // Best effort destroy if supported by provider/implementation
            }
        }
        _isUnlocked.value = false
    }
}
