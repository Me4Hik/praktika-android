package com.me4hik.praktika.data.mood

import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodCopyResolverTest {
    @Test
    fun feminineCopyMatchesProductContract() {
        assertEquals(
            MoodCopy(MoodLevel.VERY_LOW, "😣", "Тяжело", "Потеряла опору"),
            MoodCopyResolver.resolve(MoodLevel.VERY_LOW, QuestionWordingMode.FEMININE),
        )
        assertEquals(
            MoodCopy(MoodLevel.GREAT, "🤩", "Наполнена", "Расцветаю"),
            MoodCopyResolver.resolve(MoodLevel.GREAT, QuestionWordingMode.FEMININE),
        )
    }

    @Test
    fun masculineCopyMatchesProductContract() {
        assertEquals(
            MoodCopy(MoodLevel.LOW, "🙁", "Хрупко", "Уязвим"),
            MoodCopyResolver.resolve(MoodLevel.LOW, QuestionWordingMode.MASCULINE),
        )
        assertEquals(
            MoodCopy(MoodLevel.GREAT, "🤩", "Наполнен", "Полон сил и энергии"),
            MoodCopyResolver.resolve(MoodLevel.GREAT, QuestionWordingMode.MASCULINE),
        )
    }

    @Test
    fun neutralCopyMatchesProductContract() {
        assertEquals(
            MoodCopy(MoodLevel.VERY_LOW, "😣", "Тяжело", "Теряю опору"),
            MoodCopyResolver.resolve(MoodLevel.VERY_LOW, QuestionWordingMode.NEUTRAL),
        )
        assertEquals(
            MoodCopy(MoodLevel.GREAT, "🤩", "Наполненность", "Ощущаю наполненность"),
            MoodCopyResolver.resolve(MoodLevel.GREAT, QuestionWordingMode.NEUTRAL),
        )
    }

    @Test
    fun allModesExposeFiveLevels() {
        QuestionWordingMode.entries.forEach { mode ->
            assertEquals(5, MoodCopyResolver.all(mode).size)
            assertEquals(MoodLevel.entries, MoodCopyResolver.all(mode).map { it.level })
        }
    }
}
