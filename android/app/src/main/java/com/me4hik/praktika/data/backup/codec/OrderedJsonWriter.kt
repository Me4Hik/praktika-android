// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - deterministic ordered JSON text writer
package com.me4hik.praktika.data.backup.codec

internal class OrderedJsonWriter {
    private val buffer = StringBuilder()

    fun beginObject() {
        buffer.append('{')
    }

    fun endObject() {
        buffer.append('}')
    }

    fun beginArray() {
        buffer.append('[')
    }

    fun endArray() {
        buffer.append(']')
    }

    fun key(name: String) {
        if (needsSeparator()) {
            buffer.append(',')
        }
        writeStringValue(name)
        buffer.append(':')
    }

    fun value(value: Int) {
        appendValueSeparator()
        buffer.append(value)
    }

    fun value(value: Long) {
        appendValueSeparator()
        buffer.append(value)
    }

    fun value(value: Boolean) {
        appendValueSeparator()
        buffer.append(value)
    }

    fun value(value: String) {
        appendValueSeparator()
        writeStringValue(value)
    }

    fun nullValue() {
        appendValueSeparator()
        buffer.append("null")
    }

    fun rawNestedJson(json: String) {
        appendValueSeparator()
        buffer.append(json)
    }

    fun toJsonString(): String = buffer.toString()

    private fun needsSeparator(): Boolean {
        val last = buffer.lastOrNull() ?: return false
        return last != '{' && last != '[' && last != ':'
    }

    private fun appendValueSeparator() {
        if (needsSeparator()) {
            buffer.append(',')
        }
    }

    private fun writeStringValue(value: String) {
        buffer.append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> buffer.append("\\\\")
                '"' -> buffer.append("\\\"")
                '\b' -> buffer.append("\\b")
                '\u000C' -> buffer.append("\\f")
                '\n' -> buffer.append("\\n")
                '\r' -> buffer.append("\\r")
                '\t' -> buffer.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        buffer.append("\\u")
                        buffer.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        buffer.append(char)
                    }
                }
            }
        }
        buffer.append('"')
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
