package com.janreins.vaultlock.crypto

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class BackupEnvelopeTest {
    private fun password() = "separate backup password".toCharArray()
    @Test fun `round trip is portable and randomized`() {
        val clear = "private credentials".toByteArray()
        val pass = password()
        val first = BackupEnvelope.encrypt(clear, pass)
        assertTrue(pass.all { it == '\u0000' })
        val second = BackupEnvelope.encrypt(clear, password())
        assertFalse(first.contentEquals(second))
        assertArrayEquals(clear, BackupEnvelope.decrypt(first, password()))
    }
    @Test fun `wrong password and changed header or ciphertext are rejected`() {
        val encrypted = BackupEnvelope.encrypt("secret".toByteArray(), password())
        assertThrows(Exception::class.java) { BackupEnvelope.decrypt(encrypted, "incorrect".toCharArray()) }
        for (offset in listOf(0, 8, 16, 48, encrypted.lastIndex)) {
            val bad = encrypted.copyOf()
            bad[offset] = (bad[offset].toInt() xor 1).toByte()
            assertThrows(Exception::class.java) { BackupEnvelope.decrypt(bad, password()) }
        }
    }
    @Test fun `unbounded work factors unsupported versions and truncated files are rejected`() {
        val encrypted = BackupEnvelope.encrypt("secret".toByteArray(), password())
        for (iterations in listOf(0, 149_999, 2_000_001, Int.MAX_VALUE)) {
            val bad = encrypted.copyOf()
            ByteBuffer.wrap(bad).putInt(12, iterations)
            assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.decrypt(bad, password()) }
        }
        val future = encrypted.copyOf()
        ByteBuffer.wrap(future).putInt(8, 99)
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.decrypt(future, password()) }
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.decrypt(encrypted.copyOf(59), password()) }
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.decrypt(ByteArray(BackupEnvelope.MAX_BYTES + 1), password()) }
    }
}
