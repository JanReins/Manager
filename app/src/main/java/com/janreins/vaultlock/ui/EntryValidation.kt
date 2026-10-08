package com.janreins.vaultlock.ui

import com.janreins.vaultlock.crypto.TotpHelper

/** Accepts raw Base32 secrets (including grouping) and otpauth TOTP URIs. */
object EntryValidation {
    fun normalizeTotpSecret(secret: String): String =
        if (TotpHelper.isOtpauthUri(secret)) secret.trim() else TotpHelper.normalizeBase32(secret)

    fun isValidTotpSecret(secret: String): Boolean {
        if (TotpHelper.isOtpauthUri(secret)) return TotpHelper.parse(secret) != null
        val normalized = normalizeTotpSecret(secret)
        return normalized.isEmpty() || TotpHelper.isValidBase32Secret(normalized)
    }
}
