package com.janreins.vaultlock.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.Locale
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

    @Test
    fun `RFC 6238 Appendix B vectors for all three algorithms`() {
        val seeds = listOf(
            "SHA1" to "12345678901234567890",
            "SHA256" to "12345678901234567890123456789012",
            "SHA512" to "1234567890123456789012345678901234567890123456789012345678901234"
        )
        val vectors = listOf(
            59L to listOf("94287082", "46119246", "90693936"),
            1111111109L to listOf("07081804", "68084774", "25091201"),
            1111111111L to listOf("14050471", "67062674", "99943326"),
            1234567890L to listOf("89005924", "91819424", "93441116"),
            2000000000L to listOf("69279037", "90698825", "38618901"),
            20000000000L to listOf("65353130", "77737706", "47863826")
        )
        for ((seconds, expected) in vectors) {
            for ((index, seed) in seeds.withIndex()) {
                val uri = "otpauth://totp/RFC?secret=${encodeBase32(seed.second)}&algorithm=${seed.first}&digits=8"
                assertEquals("${seed.first} at $seconds", expected[index], TotpHelper.generateTotp(uri, seconds * 1000))
            }
        }
    }

    @Test
    fun `otpauth defaults and URL decoded parameters`() {
        assertEquals(TotpParams("MY======"), TotpHelper.parse("otpauth://totp/Example?secret=MY%3D%3D%3D%3D%3D%3D"))
        assertEquals(TotpParams("JBSWY3DP"), TotpHelper.parse("otpauth://totp/Example?secret=jbswy3dp&issuer=Example"))
        val uri = "otpauth://totp/Example?secret=JBSWY3DP&algorithm=sha256&digits=7&period=60"
        assertEquals(TotpParams("JBSWY3DP", "SHA256", 7, 60), TotpHelper.parse(uri))
        assertEquals(50, TotpHelper.getRemainingSeconds(uri, 10_000L))
        assertEquals(60, TotpHelper.getRemainingSeconds(uri, 60_000L))
        assertEquals(TotpHelper.generateTotp(uri, 0L), TotpHelper.generateTotp(uri, 59_000L))
        assertEquals(TotpHelper.generateTotp("JBSWY3DP", 59_000L), TotpHelper.generateTotp("otpauth://totp/Example?secret=JBSWY3DP", 59_000L))
    }

    @Test
    fun `legacy raw secret overrides still work`() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("94287082", TotpHelper.generateTotp(secret, 119_000L, timeStepSeconds = 60, codeDigits = 8))
        assertEquals(1, TotpHelper.getRemainingSeconds(119_000L, timeStepSeconds = 60))
        assertEquals(20, TotpHelper.getRemainingSeconds(secret, 10_000L))
    }

    @Test
    fun `invalid otpauth parameters produce no code`() {
        val base = "otpauth://totp/Example?secret=JBSWY3DP"
        val invalid = listOf(
            "$base&digits=5", "$base&digits=9", "$base&digits=", "$base&digits=abc",
            "$base&algorithm=MD5", "$base&algorithm=", "$base&period=0", "$base&period=14",
            "$base&period=121", "$base&period=abc", "$base&secret=MY",
            "otpauth://hotp/Example?secret=JBSWY3DP", "otpauth://totp/Example",
            "otpauth://totp/Example?secret=", "otpauth://totp/Example?secret=MZ",
            "otpauth://totp/Example?secret=MY%ZZ"
        )
        for (uri in invalid) {
            assertNull(uri, TotpHelper.parse(uri))
            assertNull(uri, TotpHelper.generateTotp(uri, 59_000L))
            assertEquals(uri, 0, TotpHelper.getRemainingSeconds(uri, 59_000L))
        }
    }

    @Test
    fun `codes contain only ASCII digits under Arabic locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar"))
            val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
            val code = TotpHelper.generateTotp(secret, 59_000L)!!
            assertEquals("287082", code)
            assertTrue(code.all { it in '0'..'9' })
            assertEquals("07081804", TotpHelper.generateTotp("otpauth://totp/RFC?secret=$secret&digits=8", 1111111109_000L))
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun encodeBase32(value: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in value.toByteArray(Charsets.US_ASCII)) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                result.append(alphabet[(buffer ushr bits) and 31])
            }
        }
        if (bits > 0) result.append(alphabet[(buffer shl (5 - bits)) and 31])
        return result.toString()
    }
}
