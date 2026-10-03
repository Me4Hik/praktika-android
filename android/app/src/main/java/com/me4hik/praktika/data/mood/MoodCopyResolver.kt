package com.me4hik.praktika.data.mood

import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.preferences.QuestionWordingMode

/**
 * Gender-aware mood presentation copy. Domain stores only [MoodLevel].
 */
object MoodCopyResolver {
    fun resolve(level: MoodLevel, mode: QuestionWordingMode): MoodCopy {
        val variant = when (mode) {
            QuestionWordingMode.FEMININE -> FEMININE
            QuestionWordingMode.MASCULINE -> MASCULINE
            QuestionWordingMode.NEUTRAL -> NEUTRAL
        }
        return variant.getValue(level)
    }

    fun all(mode: QuestionWordingMode): List<MoodCopy> {
        return MoodLevel.entries.map { resolve(it, mode) }
    }

    private data class Variant(val emoji: String, val title: String, val explanation: String)

    private fun Map<MoodLevel, Variant>.toCopies(): Map<MoodLevel, MoodCopy> {
        return mapValues { (level, variant) ->
            MoodCopy(
                level = level,
                emoji = variant.emoji,
                title = variant.title,
                explanation = variant.explanation,
            )
        }
    }

    private val FEMININE: Map<MoodLevel, MoodCopy> = mapOf(
        MoodLevel.VERY_LOW to Variant("😣", "Тяжело", "Потеряла опору"),
        MoodLevel.LOW to Variant("🙁", "Хрупко", "Уязвима"),
        MoodLevel.NEUTRAL to Variant("😐", "Ровно", "Прислушиваюсь к себе"),
        MoodLevel.GOOD to Variant("🙂", "Светло", "В контакте с собой"),
        MoodLevel.GREAT to Variant("🤩", "Наполнена", "Расцветаю"),
    ).toCopies()

    private val MASCULINE: Map<MoodLevel, MoodCopy> = mapOf(
        MoodLevel.VERY_LOW to Variant("😣", "Тяжело", "Потерял опору"),
        MoodLevel.LOW to Variant("🙁", "Хрупко", "Уязвим"),
        MoodLevel.NEUTRAL to Variant("😐", "Ровно", "Прислушиваюсь к себе"),
        MoodLevel.GOOD to Variant("🙂", "Светло", "В контакте с собой"),
        MoodLevel.GREAT to Variant("🤩", "Наполнен", "Полон сил и энергии"),
    ).toCopies()

    private val NEUTRAL: Map<MoodLevel, MoodCopy> = mapOf(
        MoodLevel.VERY_LOW to Variant("😣", "Тяжело", "Теряю опору"),
        MoodLevel.LOW to Variant("🙁", "Хрупко", "Чувствую уязвимость"),
        MoodLevel.NEUTRAL to Variant("😐", "Ровно", "Прислушиваюсь к себе"),
        MoodLevel.GOOD to Variant("🙂", "Светло", "В контакте с собой"),
        MoodLevel.GREAT to Variant("🤩", "Наполненность", "Ощущаю наполненность"),
    ).toCopies()
}
