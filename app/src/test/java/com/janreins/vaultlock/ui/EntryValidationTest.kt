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
        for (secret in listOf("A", "ABC", "ABCDEF", "MZ", "MZXW6YR", "MY=", "MY=======", "M=Y", "12345678", "otpauth://totp/example?secret=MY")) {
            assertFalse(secret, EntryValidation.isValidTotpSecret(secret))
        }
    }
}
