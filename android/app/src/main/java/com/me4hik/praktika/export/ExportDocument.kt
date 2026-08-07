// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - результат export formatter
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - общий документ export с bytes и MIME
package com.me4hik.praktika.export

data class ExportDocument(
    val suggestedFileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportDocument) return false
        return suggestedFileName == other.suggestedFileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = suggestedFileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
