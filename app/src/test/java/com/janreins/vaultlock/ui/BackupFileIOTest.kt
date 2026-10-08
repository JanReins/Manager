package com.janreins.vaultlock.ui

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BackupFileIOTest {
    @Test
    fun `reads a valid-sized payload unchanged`() {
        val data = ByteArray(1024) { it.toByte() }
        assertArrayEquals(data, BackupFileIO.readBounded(ByteArrayInputStream(data)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated payloads`() {
        BackupFileIO.readBounded(ByteArrayInputStream(ByteArray(27)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects oversized payloads`() {
        BackupFileIO.readBounded(ByteArrayInputStream(ByteArray(BackupFileIO.MAX_BACKUP_BYTES + 1)))
    }
}
