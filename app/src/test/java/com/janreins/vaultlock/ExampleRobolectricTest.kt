package com.janreins.vaultlock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.janreins.vaultlock.crypto.CryptoManager
import com.janreins.vaultlock.crypto.SessionManager
import com.janreins.vaultlock.generator.GeneratorOptions
import com.janreins.vaultlock.generator.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VaultLock", appName)
    }

    @Test
    fun `crypto encryption and decryption works with derived key`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey("MySecretMasterPassword123!".toCharArray(), salt)
        val plain = "SuperSecretVaultData!#$%"

        val cipherText = CryptoManager.encrypt(plain, key)
        assertTrue(cipherText.isNotEmpty())

        val decrypted = CryptoManager.decrypt(cipherText, key)
        assertEquals(plain, decrypted)
    }

    @Test
    fun `session manager locks and wipes key`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveKey("SamplePass123!".toCharArray(), salt)

        SessionManager.setKey(key)
        assertTrue(SessionManager.hasKey())
        assertTrue(SessionManager.isUnlocked.value)

        SessionManager.lock()
        assertFalse(SessionManager.hasKey())
        assertFalse(SessionManager.isUnlocked.value)
    }

    @Test
    fun `password generator produces expected length and characters`() {
        val options = GeneratorOptions(
            length = 24,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true
        )
        val password = PasswordGenerator.generate(options)
        assertEquals(24, password.length)
        assertNotNull(PasswordGenerator.evaluateStrength(password))
    }
}
