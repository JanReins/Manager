package com.janreins.vaultlock.ui

import java.util.Locale

/** Accepts raw Base32 secrets, including grouping spaces and hyphens. */
object EntryValidation {
    fun normalizeTotpSecret(secret: String): String = secret
        .filterNot { it.isWhitespace() || it == '-' }
        .uppercase(Locale.ROOT)

    fun isValidTotpSecret(secret: String): Boolean {
        val normalized = normalizeTotpSecret(secret)
        if (normalized.isEmpty()) return true
        val data = normalized.trimEnd('=')
        if (data.length < 2 || data.any { it !in 'A'..'Z' && it !in '2'..'7' }) return false
        val remainder = data.length % 8
        if (remainder !in setOf(0, 2, 4, 5, 7)) return false
        val padding = normalized.length - data.length
        if (padding != 0 && padding != (8 - remainder) % 8) return false
        // RFC 4648 requires unused bits in the final symbol to be zero.
        val unusedBits = (data.length * 5) % 8
        val value = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(data.last())
        return (value and ((1 shl unusedBits) - 1)) == 0
    }
}
