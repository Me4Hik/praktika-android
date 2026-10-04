package com.me4hik.praktika.data.preferences

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class QuestionDisplayTextResolverTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun resolve_allQuestionsAllModes() {
        val resources = localizedResources(Locale("ru"))
        for (id in 1..21) {
            val masculine = QuestionDisplayTextResolver.resolve(
                resources,
                id,
                QuestionWordingMode.MASCULINE,
            )
            val feminine = QuestionDisplayTextResolver.resolve(
                resources,
                id,
                QuestionWordingMode.FEMININE,
            )
            val neutral = QuestionDisplayTextResolver.resolve(
                resources,
                id,
                QuestionWordingMode.NEUTRAL,
            )
            if (id in QuestionDisplayTextResolver.WORDING_DEPENDENT_QUESTION_IDS) {
                assertEquals(expectedFeminine(id), feminine)
                assertEquals(expectedNeutral(id), neutral)
                assertEquals(expectedMasculine(id), masculine)
            } else {
                assertEquals(masculine, feminine)
                assertEquals(masculine, neutral)
            }
        }
    }

    @Test
    fun resolve_englishLocale_usesEnglishCopy() {
        val resources = localizedResources(Locale.ENGLISH)
        assertEquals(
            "What color feels most like the real me right now?",
            QuestionDisplayTextResolver.resolve(
                resources,
                2,
                QuestionWordingMode.MASCULINE,
            ),
        )
        assertEquals(
            "What color describes me best right now?",
            QuestionDisplayTextResolver.resolve(
                resources,
                2,
                QuestionWordingMode.NEUTRAL,
            ),
        )
    }

    private fun localizedResources(locale: Locale) =
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) },
        ).resources

    private fun expectedMasculine(id: Int): String = when (id) {
        2 -> "Какой настоящий я сейчас по цвету?"
        5 -> "Какой настоящий я сейчас по запаху?"
        8 -> "Какой настоящий я сейчас по звуку?"
        11 -> "Какой настоящий я сейчас на ощупь?"
        else -> error("unexpected id $id")
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
