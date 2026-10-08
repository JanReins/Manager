package com.janreins.vaultlock.ui

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Limits user-selected files before they reach the backup decoder. */
object BackupFileIO {
    const val MAX_BACKUP_BYTES = 16 * 1024 * 1024

    fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size().toLong() + count <= MAX_BACKUP_BYTES) {
                "Backup exceeds the 16 MiB import limit."
            }
            output.write(buffer, 0, count)
        }
        require(output.size() >= 28) { "File is too short to be an encrypted backup." }
        return output.toByteArray()
    }
}
