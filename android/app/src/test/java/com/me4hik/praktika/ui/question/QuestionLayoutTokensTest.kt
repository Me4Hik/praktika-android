package com.me4hik.praktika.ui.question

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionLayoutTokensTest {

    @Test
    fun phone430_isCompact() {
        val tokens = questionLayoutTokensFor(430)
        assertEquals(QuestionWidthBucket.Compact, tokens.widthBucket)
        assertFalse(tokens.useCenteredCluster)
    }

    @Test
    fun boundary559_isCompact() {
        val tokens = questionLayoutTokensFor(559)
        assertEquals(QuestionWidthBucket.Compact, tokens.widthBucket)
        assertFalse(tokens.useCenteredCluster)
    }

    @Test
    fun boundary560_isFullMedium() {
        assertFullMedium(questionLayoutTokensFor(560))
    }

    @Test
    fun boundary599_isFullMedium() {
        assertFullMedium(questionLayoutTokensFor(599))
    }

    @Test
    fun boundary600_isFullMedium() {
        assertFullMedium(questionLayoutTokensFor(600))
    }

    @Test
    fun width800_isMedium() {
        assertFullMedium(questionLayoutTokensFor(800))
    }

    @Test
    fun boundary839_isMedium() {
        assertFullMedium(questionLayoutTokensFor(839))
    }

    @Test
    fun boundary840_isExpanded() {
        val tokens = questionLayoutTokensFor(840)
        assertEquals(QuestionWidthBucket.Expanded, tokens.widthBucket)
        assertTrue(tokens.useCenteredCluster)
        assertEquals(560.dp, tokens.contentMaxWidth)
    }

    private fun assertFullMedium(tokens: QuestionLayoutTokens) {
        assertEquals(QuestionWidthBucket.Medium, tokens.widthBucket)
        assertTrue(tokens.useCenteredCluster)
        assertEquals(40.dp, tokens.horizontalPadding)
        assertEquals(520.dp, tokens.contentMaxWidth)
        assertEquals(32.sp, tokens.questionTextStyle.fontSize)
        assertEquals(42.sp, tokens.questionTextStyle.lineHeight)
        assertEquals(0.58f, tokens.orbWidthFraction)
        assertEquals(300.dp, tokens.orbMaxWidth)
    }
}
