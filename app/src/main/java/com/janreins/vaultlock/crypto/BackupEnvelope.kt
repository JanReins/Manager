package com.janreins.vaultlock.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable v2: magic, version, bounded PBKDF2 work factor, salt, IV, ciphertext and GCM tag.
 * The entire fixed-size header is authenticated. No installation key is needed to restore.
 */
object BackupEnvelope {
    const val MAX_BYTES = 16 * 1024 * 1024
    private val MAGIC = "VLOCKBKP".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 2
    private const val ITERATIONS = 600_000
    private const val HEADER_BYTES = 8 + 4 + 4 + 32 + 12
    private val random = SecureRandom()

    fun isPortable(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        try {
            require(password.size >= 12) { "Use at least 12 characters for the backup password." }
            require(plaintext.size <= MAX_BYTES - HEADER_BYTES - 16) { "Backup exceeds 16 MiB." }
            val salt = ByteArray(32).also(random::nextBytes)
            val iv = ByteArray(12).also(random::nextBytes)
            val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).putInt(VERSION)
                .putInt(ITERATIONS).put(salt).put(iv).array()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plaintext)
        } finally {
            password.fill('\u0000')
        }
    }

    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        try {
            require(bytes.size in (HEADER_BYTES + 16)..MAX_BYTES) { "Invalid backup size." }
            require(isPortable(bytes)) { "Not a portable VaultLock backup." }
            val header = bytes.copyOfRange(0, HEADER_BYTES)
            val buffer = ByteBuffer.wrap(header).apply { position(MAGIC.size) }
            require(buffer.int == VERSION) { "Unsupported backup version." }
            val iterations = buffer.int
            require(iterations in 150_000..2_000_000) { "Unsupported backup work factor." }
            val salt = ByteArray(32).also { buffer.get(it) }
            val iv = ByteArray(12).also { buffer.get(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derive(password, salt, iterations), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            return cipher.doFinal(bytes, HEADER_BYTES, bytes.size - HEADER_BYTES)
        } finally {
            password.fill('\u0000')
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }
}
