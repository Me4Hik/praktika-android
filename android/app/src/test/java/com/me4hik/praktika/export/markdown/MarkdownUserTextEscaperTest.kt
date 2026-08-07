// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - unit tests MarkdownUserTextEscaper
package com.me4hik.praktika.export.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownUserTextEscaperTest {
    @Test
    fun escapesHeadingPrefix() {
        assertEquals("\\# Заголовок", MarkdownUserTextEscaper.escapeLine("# Заголовок"))
    }

    @Test
    fun escapesBlockquotePrefix() {
        assertEquals("\\> цитата", MarkdownUserTextEscaper.escapeLine("> цитата"))
    }

    @Test
    fun escapesListPrefixes() {
        assertEquals("\\- пункт", MarkdownUserTextEscaper.escapeLine("- пункт"))
        assertEquals("\\+ пункт", MarkdownUserTextEscaper.escapeLine("+ пункт"))
        assertEquals("\\* пункт", MarkdownUserTextEscaper.escapeLine("* пункт"))
        assertEquals("\\1. пункт", MarkdownUserTextEscaper.escapeLine("1. пункт"))
    }

    @Test
    fun escapesFencedMarker() {
        assertEquals("\\`\\`\\`kotlin", MarkdownUserTextEscaper.escapeLine("```kotlin"))
    }

    @Test
    fun preservesMultilineCyrillicAndEmoji() {
        val input = "Строка один\n\nСтрока два 🎉"
        assertEquals(input, MarkdownUserTextEscaper.escapeBlock(input))
    }

    @Test
    fun preservesInlineMarkdownInsideLine() {
        assertEquals("**bold** _italic_", MarkdownUserTextEscaper.escapeBlock("**bold** _italic_"))
    }
}
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
