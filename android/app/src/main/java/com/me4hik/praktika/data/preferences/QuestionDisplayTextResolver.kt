package com.me4hik.praktika.data.preferences

import android.content.res.Resources
import com.me4hik.praktika.R

/**
 * Resolves user-facing question wording for a given mode from localized resources.
 * Canonical [QuestionEntity.text] / seed remains masculine; only ids 2/5/8/11 vary by mode.
 */
object QuestionDisplayTextResolver {
    val WORDING_DEPENDENT_QUESTION_IDS: Set<Int> = setOf(2, 5, 8, 11)

    fun isWordingDependent(questionId: Int): Boolean = questionId in WORDING_DEPENDENT_QUESTION_IDS

    fun resolve(
        resources: Resources,
        questionId: Int,
        mode: QuestionWordingMode,
    ): String {
        val resId = when (mode) {
            QuestionWordingMode.MASCULINE -> masculineResId(questionId)
            QuestionWordingMode.FEMININE -> {
                if (isWordingDependent(questionId)) {
                    feminineResId(questionId)
                } else {
                    masculineResId(questionId)
                }
            }
            QuestionWordingMode.NEUTRAL -> {
                if (isWordingDependent(questionId)) {
                    neutralResId(questionId)
                } else {
                    masculineResId(questionId)
                }
            }
        }
        return resources.getString(resId)
    }

    private fun masculineResId(questionId: Int): Int = when (questionId) {
        1 -> R.string.content_question_1
        2 -> R.string.content_question_2
        3 -> R.string.content_question_3
        4 -> R.string.content_question_4
        5 -> R.string.content_question_5
        6 -> R.string.content_question_6
        7 -> R.string.content_question_7
        8 -> R.string.content_question_8
        9 -> R.string.content_question_9
        10 -> R.string.content_question_10
        11 -> R.string.content_question_11
        12 -> R.string.content_question_12
        13 -> R.string.content_question_13
        14 -> R.string.content_question_14
        15 -> R.string.content_question_15
        16 -> R.string.content_question_16
        17 -> R.string.content_question_17
        18 -> R.string.content_question_18
        19 -> R.string.content_question_19
        20 -> R.string.content_question_20
        21 -> R.string.content_question_21
        else -> error("Unknown questionId=$questionId")
    }

    private fun feminineResId(questionId: Int): Int = when (questionId) {
        2 -> R.string.content_question_2_feminine
        5 -> R.string.content_question_5_feminine
        8 -> R.string.content_question_8_feminine
        11 -> R.string.content_question_11_feminine
        else -> error("No feminine wording for questionId=$questionId")
    }

    private fun neutralResId(questionId: Int): Int = when (questionId) {
        2 -> R.string.content_question_2_neutral
        5 -> R.string.content_question_5_neutral
        8 -> R.string.content_question_8_neutral
        11 -> R.string.content_question_11_neutral
        else -> error("No neutral wording for questionId=$questionId")
    }
}
