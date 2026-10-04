package com.me4hik.praktika.data.mood

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class MoodCopyResolverTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun feminineCopyMatchesProductContract() {
        val resources = localizedResources(Locale("ru"))
        assertEquals(
            MoodCopy(MoodLevel.VERY_LOW, "😣", "Тяжело", "Потеряла опору"),
            MoodCopyResolver.resolve(resources, MoodLevel.VERY_LOW, QuestionWordingMode.FEMININE),
        )
        assertEquals(
            MoodCopy(MoodLevel.GREAT, "🤩", "Наполнена", "Расцветаю"),
            MoodCopyResolver.resolve(resources, MoodLevel.GREAT, QuestionWordingMode.FEMININE),
        )
    }

    @Test
    fun masculineCopyMatchesProductContract() {
        val resources = localizedResources(Locale("ru"))
        assertEquals(
            MoodCopy(MoodLevel.LOW, "🙁", "Хрупко", "Уязвим"),
            MoodCopyResolver.resolve(resources, MoodLevel.LOW, QuestionWordingMode.MASCULINE),
        )
        assertEquals(
            MoodCopy(MoodLevel.GREAT, "🤩", "Наполнен", "Полон сил и энергии"),
            MoodCopyResolver.resolve(resources, MoodLevel.GREAT, QuestionWordingMode.MASCULINE),
        )
    }

    @Test
    fun neutralCopyMatchesProductContract() {
        val resources = localizedResources(Locale("ru"))
        assertEquals(
            MoodCopy(MoodLevel.VERY_LOW, "😣", "Тяжело", "Теряю опору"),
            MoodCopyResolver.resolve(resources, MoodLevel.VERY_LOW, QuestionWordingMode.NEUTRAL),
        )
        assertEquals(
            MoodCopy(MoodLevel.GREAT, "🤩", "Наполненность", "Ощущаю наполненность"),
            MoodCopyResolver.resolve(resources, MoodLevel.GREAT, QuestionWordingMode.NEUTRAL),
        )
    }

    @Test
    fun englishLocale_usesEnglishMoodCopy() {
        val resources = localizedResources(Locale.ENGLISH)
        assertEquals(
            MoodCopy(MoodLevel.GREAT, "🤩", "Fulfilled", "Blooming"),
            MoodCopyResolver.resolve(resources, MoodLevel.GREAT, QuestionWordingMode.FEMININE),
        )
    }

    @Test
    fun allModesExposeFiveLevels() {
        val resources = localizedResources(Locale("ru"))
        QuestionWordingMode.entries.forEach { mode ->
            assertEquals(5, MoodCopyResolver.all(resources, mode).size)
            assertEquals(MoodLevel.entries, MoodCopyResolver.all(resources, mode).map { it.level })
        }
    }

    private fun localizedResources(locale: Locale) =
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) },
        ).resources
}
