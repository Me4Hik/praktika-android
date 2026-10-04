package com.me4hik.praktika.data.mood

import android.content.res.Resources
import com.me4hik.praktika.R
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.preferences.QuestionWordingMode

/**
 * Gender-aware mood presentation copy. Domain stores only [MoodLevel].
 */
object MoodCopyResolver {
    private const val EMOJI_VERY_LOW = "😣"
    private const val EMOJI_LOW = "🙁"
    private const val EMOJI_NEUTRAL = "😐"
    private const val EMOJI_GOOD = "🙂"
    private const val EMOJI_GREAT = "🤩"

    fun resolve(
        resources: Resources,
        level: MoodLevel,
        mode: QuestionWordingMode,
    ): MoodCopy {
        val (titleRes, explanationRes) = stringIds(level, mode)
        return MoodCopy(
            level = level,
            emoji = emojiFor(level),
            title = resources.getString(titleRes),
            explanation = resources.getString(explanationRes),
        )
    }

    fun all(resources: Resources, mode: QuestionWordingMode): List<MoodCopy> {
        return MoodLevel.entries.map { resolve(resources, it, mode) }
    }

    private fun emojiFor(level: MoodLevel): String = when (level) {
        MoodLevel.VERY_LOW -> EMOJI_VERY_LOW
        MoodLevel.LOW -> EMOJI_LOW
        MoodLevel.NEUTRAL -> EMOJI_NEUTRAL
        MoodLevel.GOOD -> EMOJI_GOOD
        MoodLevel.GREAT -> EMOJI_GREAT
    }

    private fun stringIds(
        level: MoodLevel,
        mode: QuestionWordingMode,
    ): Pair<Int, Int> = when (mode) {
        QuestionWordingMode.FEMININE -> when (level) {
            MoodLevel.VERY_LOW ->
                R.string.content_mood_very_low_feminine_title to
                    R.string.content_mood_very_low_feminine_explanation
            MoodLevel.LOW ->
                R.string.content_mood_low_feminine_title to
                    R.string.content_mood_low_feminine_explanation
            MoodLevel.NEUTRAL ->
                R.string.content_mood_neutral_feminine_title to
                    R.string.content_mood_neutral_feminine_explanation
            MoodLevel.GOOD ->
                R.string.content_mood_good_feminine_title to
                    R.string.content_mood_good_feminine_explanation
            MoodLevel.GREAT ->
                R.string.content_mood_great_feminine_title to
                    R.string.content_mood_great_feminine_explanation
        }
        QuestionWordingMode.MASCULINE -> when (level) {
            MoodLevel.VERY_LOW ->
                R.string.content_mood_very_low_masculine_title to
                    R.string.content_mood_very_low_masculine_explanation
            MoodLevel.LOW ->
                R.string.content_mood_low_masculine_title to
                    R.string.content_mood_low_masculine_explanation
            MoodLevel.NEUTRAL ->
                R.string.content_mood_neutral_masculine_title to
                    R.string.content_mood_neutral_masculine_explanation
            MoodLevel.GOOD ->
                R.string.content_mood_good_masculine_title to
                    R.string.content_mood_good_masculine_explanation
            MoodLevel.GREAT ->
                R.string.content_mood_great_masculine_title to
                    R.string.content_mood_great_masculine_explanation
        }
        QuestionWordingMode.NEUTRAL -> when (level) {
            MoodLevel.VERY_LOW ->
                R.string.content_mood_very_low_neutral_title to
                    R.string.content_mood_very_low_neutral_explanation
            MoodLevel.LOW ->
                R.string.content_mood_low_neutral_title to
                    R.string.content_mood_low_neutral_explanation
            MoodLevel.NEUTRAL ->
                R.string.content_mood_neutral_neutral_title to
                    R.string.content_mood_neutral_neutral_explanation
            MoodLevel.GOOD ->
                R.string.content_mood_good_neutral_title to
                    R.string.content_mood_good_neutral_explanation
            MoodLevel.GREAT ->
                R.string.content_mood_great_neutral_title to
                    R.string.content_mood_great_neutral_explanation
        }
    }
}
