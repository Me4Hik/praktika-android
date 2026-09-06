package com.me4hik.praktika.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionDisplayTextResolverTest {
    private val canonicalById: Map<Int, String> = (1..21).associateWith { id ->
        when (id) {
            2 -> "Какой настоящий я сейчас по цвету?"
            5 -> "Какой настоящий я сейчас по запаху?"
            8 -> "Какой настоящий я сейчас по звуку?"
            11 -> "Какой настоящий я сейчас на ощупь?"
            else -> "Question $id"
        }
    }

    @Test
    fun resolve_allQuestionsAllModes() {
        for (id in 1..21) {
            val canonical = canonicalById.getValue(id)
            assertEquals(
                canonical,
                QuestionDisplayTextResolver.resolve(id, canonical, QuestionWordingMode.MASCULINE),
            )
            val feminine = QuestionDisplayTextResolver.resolve(id, canonical, QuestionWordingMode.FEMININE)
            val neutral = QuestionDisplayTextResolver.resolve(id, canonical, QuestionWordingMode.NEUTRAL)
            if (id in QuestionDisplayTextResolver.WORDING_DEPENDENT_QUESTION_IDS) {
                assertEquals(expectedFeminine(id), feminine)
                assertEquals(expectedNeutral(id), neutral)
            } else {
                assertEquals(canonical, feminine)
                assertEquals(canonical, neutral)
            }
        }
    }

    private fun expectedFeminine(id: Int): String = when (id) {
        2 -> "Какая я настоящая сейчас по цвету?"
        5 -> "Какая я настоящая сейчас по запаху?"
        8 -> "Какая я настоящая сейчас по звуку?"
        11 -> "Какая я настоящая сейчас на ощупь?"
        else -> error("unexpected id $id")
    }

    private fun expectedNeutral(id: Int): String = when (id) {
        2 -> "Какой цвет сейчас лучше всего меня описывает?"
        5 -> "Какой запах сейчас лучше всего меня описывает?"
        8 -> "Какой звук сейчас лучше всего меня описывает?"
        11 -> "Какое ощущение на ощупь сейчас лучше всего меня описывает?"
        else -> error("unexpected id $id")
    }
}

class QuestionWordingModeTest {
    @Test
    fun defaultIsMasculine() {
        assertEquals(QuestionWordingMode.MASCULINE, QuestionWordingMode.DEFAULT)
    }

    @Test
    fun fromStorage_knownAndUnknown() {
        assertEquals(QuestionWordingMode.FEMININE, QuestionWordingMode.fromStorage("FEMININE"))
        assertEquals(QuestionWordingMode.NEUTRAL, QuestionWordingMode.fromStorage("NEUTRAL"))
        assertEquals(QuestionWordingMode.MASCULINE, QuestionWordingMode.fromStorage("MASCULINE"))
        assertEquals(QuestionWordingMode.MASCULINE, QuestionWordingMode.fromStorage(null))
        assertEquals(QuestionWordingMode.MASCULINE, QuestionWordingMode.fromStorage(""))
        assertEquals(QuestionWordingMode.MASCULINE, QuestionWordingMode.fromStorage("male"))
        assertEquals(QuestionWordingMode.MASCULINE, QuestionWordingMode.fromStorage("feminine"))
    }
}
