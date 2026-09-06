package com.janreins.vaultlock.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure Kotlin implementation of Time-based One-Time Password (TOTP) algorithm (RFC 6238 / RFC 4226).
 * Computes 6-digit TOTP codes using HMAC-SHA1 and standard 30-second time steps.
 * Never logs or prints sensitive secret material.
 */
object TotpHelper {

    private const val DEFAULT_TIME_STEP_SECONDS = 30
    private const val DEFAULT_DIGITS = 6
    private const val HMAC_ALGORITHM = "HmacSHA1"

    /**
     * Generates a 6-digit TOTP code for a given Base32 secret at the specified timestamp.
     * Returns null if secret is blank or invalid Base32.
     */
    fun generateTotp(
        secret: String,
        timeMillis: Long = System.currentTimeMillis(),
        timeStepSeconds: Int = DEFAULT_TIME_STEP_SECONDS,
        codeDigits: Int = DEFAULT_DIGITS
    ): String? {
        val cleanSecret = secret.replace("\\s+".toRegex(), "").replace("-", "").uppercase()
        if (cleanSecret.isEmpty()) return null

        val keyBytes = decodeBase32(cleanSecret) ?: return null
        if (keyBytes.isEmpty()) return null

        return try {
            val timeSeconds = timeMillis / 1000L
            val counter = timeSeconds / timeStepSeconds

            val buffer = ByteBuffer.allocate(8)
            buffer.putLong(counter)
            val counterBytes = buffer.array()

            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(keyBytes, HMAC_ALGORITHM))
            val hmac = mac.doFinal(counterBytes)

            val offset = (hmac[hmac.size - 1].toInt() and 0x0F)
            val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                    ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                    (hmac[offset + 3].toInt() and 0xFF)

            val modulus = Math.pow(10.0, codeDigits.toDouble()).toInt()
            val otp = binary % modulus
            String.format("%0${codeDigits}d", otp)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the remaining seconds in the current TOTP step window (0 to timeStepSeconds).
     */
    fun getRemainingSeconds(
        timeMillis: Long = System.currentTimeMillis(),
        timeStepSeconds: Int = DEFAULT_TIME_STEP_SECONDS
    ): Int {
        val currentSeconds = (timeMillis / 1000L) % timeStepSeconds
        return (timeStepSeconds - currentSeconds.toInt()).coerceIn(0, timeStepSeconds)
    }

    /**
     * Decodes a Base32 encoded string into raw bytes.
     * Standard RFC 4648 Base32 alphabet: A-Z, 2-7.
     */
    fun decodeBase32(base32: String): ByteArray? {
        val clean = base32.trimEnd('=').uppercase().replace("\\s+".toRegex(), "")
        if (clean.isEmpty()) return ByteArray(0)

        var buffer = 0
        var bitsLeft = 0
        val result = mutableListOf<Byte>()

        for (ch in clean) {
            val value = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in '2'..'7' -> ch - '2' + 26
                else -> return null // Invalid Base32 char
            }
            buffer = (buffer shl 5) or (value and 0x1F)
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                val byteVal = (buffer shr bitsLeft) and 0xFF
                result.add(byteVal.toByte())
            }
        }
        return result.toByteArray()
    }
}
