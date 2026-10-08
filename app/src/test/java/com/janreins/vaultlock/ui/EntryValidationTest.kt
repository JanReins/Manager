package com.janreins.vaultlock.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryValidationTest {
    @Test
    fun `accepts empty and valid padded or grouped secrets`() {
        for (secret in listOf("", "MY======", "MY", "MZXQ====", "MZXW6===", "MZXW6YQ=", "MZXW6YTB", "jbsw y3dp-ehpk3pxp")) {
            assertTrue(secret, EntryValidation.isValidTotpSecret(secret))
        }
        assertEquals("JBSWY3DPEHPK3PXP", EntryValidation.normalizeTotpSecret("jbsw y3dp-ehpk3pxp"))
    }

    @Test
    fun `rejects malformed secrets rather than silently truncating bits`() {
        for (secret in listOf("A", "ABC", "ABCDEF", "MZ", "MZXW6YR", "MY=", "MY=======", "M=Y", "12345678")) {
            assertFalse(secret, EntryValidation.isValidTotpSecret(secret))
        }
    }

    @Test
    fun `accepts valid TOTP URIs and preserves URI case and punctuation on save`() {
        val uri = "otpauth://totp/Example-Account?secret=jbswy3dp&algorithm=sha512&digits=8&period=60&issuer=My%20Service"
        assertTrue(EntryValidation.isValidTotpSecret(uri))
        assertTrue(EntryValidation.isValidTotpSecret("otpauth://totp/example?secret=MY"))
        assertTrue(EntryValidation.isValidTotpSecret("otpauth://totp/example?secret=MY%3D%3D%3D%3D%3D%3D"))
        assertEquals(uri, EntryValidation.normalizeTotpSecret("  $uri  "))
    }

    @Test
    fun `rejects invalid URIs including malformed embedded Base32`() {
        for (uri in listOf(
            "otpauth://hotp/example?secret=MY",
            "otpauth://totp/example", "otpauth://totp/example?secret=",
            "otpauth://totp/example?secret=MZ", "otpauth://totp/example?secret=MY%3D",
            "otpauth://totp/example?secret=MY&algorithm=MD5",
            "otpauth://totp/example?secret=MY&digits=9",
            "otpauth://totp/example?secret=MY&period=121"
        )) assertFalse(uri, EntryValidation.isValidTotpSecret(uri))
    }
}
