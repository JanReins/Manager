package com.janreins.vaultlock.crypto

import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TotpParams(
    val secret: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30
)

/**
 * Pure Kotlin implementation of Time-based One-Time Password (TOTP) algorithm (RFC 6238 / RFC 4226).
 * Accepts raw Base32 secrets or otpauth TOTP URIs with RFC 6238 parameters.
 * Never logs or prints sensitive secret material.
 */
object TotpHelper {

    private const val DEFAULT_TIME_STEP_SECONDS = 30
    private const val DEFAULT_DIGITS = 6

    fun isOtpauthUri(secret: String): Boolean =
        secret.trim().startsWith("otpauth:", ignoreCase = true)

    fun normalizeBase32(secret: String): String = secret
        .filterNot { it.isWhitespace() || it == '-' }
        .uppercase(Locale.ROOT)

    /** Strict RFC 4648 validation, including padding and unused bits. */
    fun isValidBase32Secret(secret: String): Boolean {
        val normalized = normalizeBase32(secret)
        val data = normalized.trimEnd('=')
        if (data.length < 2 || data.any { it !in 'A'..'Z' && it !in '2'..'7' }) return false
        val remainder = data.length % 8
        if (remainder !in setOf(0, 2, 4, 5, 7)) return false
        val padding = normalized.length - data.length
        if (padding != 0 && padding != (8 - remainder) % 8) return false
        val unusedBits = (data.length * 5) % 8
        val value = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(data.last())
        return (value and ((1 shl unusedBits) - 1)) == 0
    }

    /** Parses locally; no network access. Raw secrets retain the legacy decoding rules. */
    fun parse(secret: String): TotpParams? {
        if (!isOtpauthUri(secret)) {
            val normalized = normalizeBase32(secret)
            if (decodeBase32(normalized)?.isNotEmpty() != true) return null
            return TotpParams(normalized)
        }
        return try {
            val uri = URI(secret.trim())
            if (!uri.scheme.equals("otpauth", ignoreCase = true) ||
                !uri.host.equals("totp", ignoreCase = true) ||
                uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null ||
                uri.rawPath.isNullOrEmpty() || uri.rawPath == "/"
            ) return null
            val query = uri.rawQuery ?: return null
            val params = mutableMapOf<String, String>()
            for (part in query.split('&')) {
                val pair = part.split('=', limit = 2)
                val name = URLDecoder.decode(pair[0], "UTF-8")
                val value = URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
                if (params.put(name, value) != null) return null
            }
            val embeddedSecret = params["secret"] ?: return null
            if (!isValidBase32Secret(embeddedSecret)) return null
            val algorithm = (params["algorithm"] ?: "SHA1").uppercase(Locale.ROOT)
            if (algorithm !in setOf("SHA1", "SHA256", "SHA512")) return null
            val digits = if ("digits" in params) params.getValue("digits").toIntOrNull() ?: return null else 6
            val period = if ("period" in params) params.getValue("period").toIntOrNull() ?: return null else 30
            if (digits !in 6..8 || period !in 15..120) return null
            TotpParams(normalizeBase32(embeddedSecret), algorithm, digits, period)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates an ASCII TOTP code. URI parameters take precedence over legacy overrides.
     * Returns null for invalid secrets or parameters.
     */
    fun generateTotp(
        secret: String,
        timeMillis: Long = System.currentTimeMillis(),
        timeStepSeconds: Int = DEFAULT_TIME_STEP_SECONDS,
        codeDigits: Int = DEFAULT_DIGITS
    ): String? {
        val params = parse(secret) ?: return null
        val period = if (isOtpauthUri(secret)) params.period else timeStepSeconds
        val digits = if (isOtpauthUri(secret)) params.digits else codeDigits
        if (period <= 0 || digits !in 1..9) return null
        val keyBytes = decodeBase32(params.secret) ?: return null
        val hmacAlgorithm = "Hmac${params.algorithm}"

        return try {
            val timeSeconds = timeMillis / 1000L
            val counter = timeSeconds / period

            val buffer = ByteBuffer.allocate(8)
            buffer.putLong(counter)
            val counterBytes = buffer.array()

            val mac = Mac.getInstance(hmacAlgorithm)
            mac.init(SecretKeySpec(keyBytes, hmacAlgorithm))
            val hmac = mac.doFinal(counterBytes)

            val offset = (hmac[hmac.size - 1].toInt() and 0x0F)
            val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                    ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                    (hmac[offset + 3].toInt() and 0xFF)

            var modulus = 1
            repeat(digits) { modulus *= 10 }
            val otp = binary % modulus
            otp.toString().padStart(digits, '0')
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

    /** Returns zero for invalid entries, otherwise uses the entry's own period. */
    fun getRemainingSeconds(secret: String, timeMillis: Long = System.currentTimeMillis()): Int {
        val params = parse(secret) ?: return 0
        return getRemainingSeconds(timeMillis, params.period)
    }

    /**
     * Decodes a Base32 encoded string into raw bytes.
     * Standard RFC 4648 Base32 alphabet: A-Z, 2-7.
     */
    fun decodeBase32(base32: String): ByteArray? {
        val clean = base32.trimEnd('=').uppercase(Locale.ROOT).replace("\\s+".toRegex(), "")
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
