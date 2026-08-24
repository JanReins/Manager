package com.example.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey

/**
 * Manages the in-memory active cryptographic session.
 * Zeroizes/wipes the session key on lock to prevent memory scraping.
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
        activeSecretKey = null
        _isUnlocked.value = false
    }
}
