package com.janreins.vaultlock.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CryptoManagerTest {

    @Test
    fun `encrypt and decrypt string roundtrip succeeds`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey("MasterPass123!".toCharArray(), salt)
        val plainText = "SensitiveVaultPassword_987#$"

        val encrypted = CryptoManager.encrypt(plainText, key)
        assertNotEquals(plainText, encrypted)
        assertTrue(encrypted.isNotEmpty())

        val decrypted = CryptoManager.decrypt(encrypted, key)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun `encrypt and decrypt empty string returns empty string`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey("Pass123!".toCharArray(), salt)

        assertEquals("", CryptoManager.encrypt("", key))
        assertEquals("", CryptoManager.decrypt("", key))
    }

    @Test
    fun `encryptBytes and decryptBytes roundtrip succeeds`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey("BackupKey456!".toCharArray(), salt)
        val data = "Raw byte payload backup test".toByteArray(Charsets.UTF_8)

        val encryptedBytes = CryptoManager.encryptBytes(data, key)
        assertTrue(encryptedBytes.size > data.size)

        val decryptedBytes = CryptoManager.decryptBytes(encryptedBytes, key)
        assertEquals(String(data, Charsets.UTF_8), String(decryptedBytes, Charsets.UTF_8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decrypt payload shorter than IV throws IllegalArgumentException`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey("Pass123!".toCharArray(), salt)
        val shortPayloadBase64 = android.util.Base64.encodeToString(ByteArray(5), android.util.Base64.NO_WRAP)

        CryptoManager.decrypt(shortPayloadBase64, key)
    }

    @Test(expected = AEADBadTagException::class)
    fun `decrypt tampered ciphertext throws AEADBadTagException`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey("Pass123!".toCharArray(), salt)
        val plainText = "AuthenticData"

        val encryptedBase64 = CryptoManager.encrypt(plainText, key)
        val decoded = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)

        // Tamper with ciphertext byte
        decoded[decoded.size - 1] = (decoded[decoded.size - 1].toInt() xor 0xFF).toByte()
        val tamperedBase64 = android.util.Base64.encodeToString(decoded, android.util.Base64.NO_WRAP)

        CryptoManager.decrypt(tamperedBase64, key)
    }
}
