package com.janreins.vaultlock.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TotpHelperTest {

    @Test
    fun `decodeBase32 decodes valid RFC 4648 Base32 strings`() {
        val secretHello = "JBSWY3DP" // Standard Base32 string for "Hello"
        val decodedHello = TotpHelper.decodeBase32(secretHello)
        assertNotNull(decodedHello)
        assertEquals("Hello", String(decodedHello!!, Charsets.UTF_8))

        val secretHelloEx = "JBSWY3DPEE======" // Standard Base32 string for "Hello!"
        val decodedHelloEx = TotpHelper.decodeBase32(secretHelloEx)
        assertNotNull(decodedHelloEx)
        assertEquals("Hello!", String(decodedHelloEx!!, Charsets.UTF_8))
    }

    @Test
    fun `decodeBase32 returns null for invalid characters`() {
        val invalidSecret = "JBSWY3DP890!" // '8', '9', '0' and '!' are invalid Base32
        val decoded = TotpHelper.decodeBase32(invalidSecret)
        assertNull(decoded)
    }

    @Test
    fun `generateTotp produces deterministic 6-digit code for known timestamp`() {
        // RFC 6238 test secret "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" (Base32 of "12345678901234567890")
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        // T0 = 59s -> time / 30 = counter 1
        val code59 = TotpHelper.generateTotp(secret, timeMillis = 59_000L)
        assertNotNull(code59)
        assertEquals(6, code59?.length)
        assertEquals("287082", code59) // Standard RFC 6238 vector for counter 1

        // T0 = 1111111109s -> counter 37037036
        val codeVector = TotpHelper.generateTotp(secret, timeMillis = 1111111109_000L)
        assertEquals("081804", codeVector) // Deterministic output for this RFC test vector
    }

    @Test
    fun `generateTotp returns null for blank secret`() {
        assertNull(TotpHelper.generateTotp(""))
        assertNull(TotpHelper.generateTotp("   "))
    }

    @Test
    fun `getRemainingSeconds returns countdown within 1 and 30`() {
        val remaining = TotpHelper.getRemainingSeconds(timeMillis = 10_000L) // 10s into 30s step
        assertEquals(20, remaining)
    }
}
