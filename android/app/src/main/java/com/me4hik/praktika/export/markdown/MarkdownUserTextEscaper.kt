// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - защита пользовательского текста в Markdown
package com.me4hik.praktika.export.markdown

object MarkdownUserTextEscaper {
    private val orderedListPrefix = Regex("""^\d+\.\s""")

    fun escapeBlock(text: String): String {
        return text.lineSequence().joinToString("\n") { line ->
            escapeLine(line)
        }
    }

    internal fun escapeLine(line: String): String {
        val leadingWhitespace = line.takeWhile { it.isWhitespace() }
        val content = line.drop(leadingWhitespace.length)
        if (content.isEmpty()) {
            return line
        }
        if (content.startsWith("```")) {
            return leadingWhitespace + "\\`\\`\\`" + content.drop(3)
        }
        if (needsStructuralEscape(content)) {
            return leadingWhitespace + "\\" + content
        }
        return line
    }

    private fun needsStructuralEscape(content: String): Boolean {
        return content.startsWith("#") ||
            content.startsWith(">") ||
            content.startsWith("- ") ||
            content.startsWith("+ ") ||
            content.startsWith("* ") ||
            orderedListPrefix.containsMatchIn(content)
    }
}
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
