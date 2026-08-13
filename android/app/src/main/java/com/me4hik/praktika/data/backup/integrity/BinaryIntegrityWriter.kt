// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - deterministic binary integrity primitives
package com.me4hik.praktika.data.backup.integrity

import java.io.ByteArrayOutputStream

internal class BinaryIntegrityWriter {
    private val buffer = ByteArrayOutputStream()

    fun toByteArray(): ByteArray = buffer.toByteArray()

    fun writeInt(value: Int) {
        buffer.write((value ushr 24) and 0xFF)
        buffer.write((value ushr 16) and 0xFF)
        buffer.write((value ushr 8) and 0xFF)
        buffer.write(value and 0xFF)
    }

    fun writeLong(value: Long) {
        buffer.write(((value ushr 56) and 0xFF).toInt())
        buffer.write(((value ushr 48) and 0xFF).toInt())
        buffer.write(((value ushr 40) and 0xFF).toInt())
        buffer.write(((value ushr 32) and 0xFF).toInt())
        buffer.write(((value ushr 24) and 0xFF).toInt())
        buffer.write(((value ushr 16) and 0xFF).toInt())
        buffer.write(((value ushr 8) and 0xFF).toInt())
        buffer.write((value and 0xFF).toInt())
    }

    fun writeBoolean(value: Boolean) {
        buffer.write(if (value) 0x01 else 0x00)
    }

    fun writeNullableLong(value: Long?) {
        if (value == null) {
            buffer.write(0x00)
        } else {
            buffer.write(0x01)
            writeLong(value)
        }
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        buffer.write(bytes)
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
