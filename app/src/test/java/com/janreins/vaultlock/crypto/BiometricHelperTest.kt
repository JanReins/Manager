package com.janreins.vaultlock.crypto

import android.app.Application
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.janreins.vaultlock.data.SecurityPreferences
import com.janreins.vaultlock.ui.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyStore
import javax.crypto.Cipher

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiometricHelperTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var securityPreferences: SecurityPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        val testPrefs = application.getSharedPreferences("test_biometric_prefs", Context.MODE_PRIVATE)
        testPrefs.edit().clear().commit()
        securityPreferences = SecurityPreferences(application, customPrefs = testPrefs)
        BiometricHelper.deleteKeystoreKey()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        BiometricHelper.deleteKeystoreKey()
        SessionManager.lock()
    }

    @Test
    fun `buildKeyGenParameterSpec creates correct spec parameters`() {
        val spec = BiometricHelper.buildKeyGenParameterSpec("TestAlias")
        assertEquals("TestAlias", spec.keystoreAlias)
        assertEquals(256, spec.keySize)
        assertTrue(spec.isUserAuthenticationRequired)
        assertEquals(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT, spec.purposes)
        assertTrue(spec.blockModes.contains(KeyProperties.BLOCK_MODE_GCM))
        assertTrue(spec.encryptionPaddings.contains(KeyProperties.ENCRYPTION_PADDING_NONE))
    }

    @Config(sdk = [29])
    @Test
    fun `buildKeyGenParameterSpec sets validity duration to minus 1 on API under 30`() {
        val spec = BiometricHelper.buildKeyGenParameterSpec("TestAlias29")
        assertEquals(-1, spec.userAuthenticationValidityDurationSeconds)
    }

    @Test
    fun `securityPreferences biometric enablement flow`() {
        assertFalse(securityPreferences.isBiometricEnabled)
        assertNull(securityPreferences.getWrappedMasterKey())

        val testWrappedKey = "VGVzdFdyYXBwZWRLZXlEYXRh"
        securityPreferences.saveBiometricWrappedKey(testWrappedKey)

        assertTrue(securityPreferences.isBiometricEnabled)
        assertEquals(testWrappedKey, securityPreferences.getWrappedMasterKey())

        securityPreferences.disableBiometric()
        assertFalse(securityPreferences.isBiometricEnabled)
        assertNull(securityPreferences.getWrappedMasterKey())
    }

    @Test
    fun `isKeyPermanentlyInvalidated detects key invalidation exception`() {
        val invalidatedException = KeyPermanentlyInvalidatedException("Key permanently invalidated")
        assertTrue(BiometricHelper.isKeyPermanentlyInvalidated(invalidatedException))

        val wrappedException = Exception("Wrapped", KeyPermanentlyInvalidatedException("Inner invalidation"))
        assertTrue(BiometricHelper.isKeyPermanentlyInvalidated(wrappedException))

        val msgException = Exception("Key permanently invalidated due to new biometric enrollment")
        assertTrue(BiometricHelper.isKeyPermanentlyInvalidated(msgException))

        val generalException = IllegalStateException("Random state error")
        assertFalse(BiometricHelper.isKeyPermanentlyInvalidated(generalException))
    }

    @Test
    fun `promptBiometricUnlock fails and invokes onInvalidated when Keystore key alias is missing`() {
        // Ensure keystore alias is deleted
        BiometricHelper.deleteKeystoreKey()

        val dummyActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val dummyWrappedKey = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo0NTY3ODk="

        var invalidatedCalled = false
        var errorMessage: String? = null

        BiometricHelper.promptBiometricUnlock(
            activity = dummyActivity,
            wrappedKeyBase64 = dummyWrappedKey,
            onInvalidated = {
                invalidatedCalled = true
            },
            onSuccess = {},
            onError = { err ->
                errorMessage = err
            }
        )

        assertTrue(invalidatedCalled)
        assertNotNull(errorMessage)
        assertTrue(errorMessage!!.contains("Biometric key missing"))
    }

    @Test
    fun `viewModel unlockWithBiometric disables biometric when wrapped key or keystore key missing`() {
        val masterPass = "ValidPass123!"
        securityPreferences.setupMasterPassword(masterPass.toCharArray())
        securityPreferences.saveBiometricWrappedKey("QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo0NTY3ODk=")
        assertTrue(securityPreferences.isBiometricEnabled)

        // Delete keystore key to simulate missing/invalid key
        BiometricHelper.deleteKeystoreKey()

        val viewModel = VaultViewModel(application, securityPreferences)
        val dummyActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

        var unlockResult = true
        var resultMessage = ""

        viewModel.unlockWithBiometric(dummyActivity) { success, msg ->
            unlockResult = success
            resultMessage = msg
        }

        assertFalse(unlockResult)
        assertFalse(viewModel.uiState.value.isBiometricEnabled)
        assertFalse(securityPreferences.isBiometricEnabled)
        assertNull(securityPreferences.getWrappedMasterKey())
    }
}
