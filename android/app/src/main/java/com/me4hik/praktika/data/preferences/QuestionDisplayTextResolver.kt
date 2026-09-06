package com.me4hik.praktika.data.preferences

/**
 * Resolves user-facing question wording for a given mode.
 * Canonical [QuestionEntity.text] / seed remains masculine; only ids 2/5/8/11 vary.
 */
object QuestionDisplayTextResolver {
    val WORDING_DEPENDENT_QUESTION_IDS: Set<Int> = setOf(2, 5, 8, 11)

    private val FEMININE_BY_ID: Map<Int, String> = mapOf(
        2 to "Какая я настоящая сейчас по цвету?",
        5 to "Какая я настоящая сейчас по запаху?",
        8 to "Какая я настоящая сейчас по звуку?",
        11 to "Какая я настоящая сейчас на ощупь?",
    )

    private val NEUTRAL_BY_ID: Map<Int, String> = mapOf(
        2 to "Какой цвет сейчас лучше всего меня описывает?",
        5 to "Какой запах сейчас лучше всего меня описывает?",
        8 to "Какой звук сейчас лучше всего меня описывает?",
        11 to "Какое ощущение на ощупь сейчас лучше всего меня описывает?",
    )

    fun isWordingDependent(questionId: Int): Boolean = questionId in WORDING_DEPENDENT_QUESTION_IDS

    fun resolve(
        questionId: Int,
        canonicalText: String,
        mode: QuestionWordingMode,
    ): String {
        return when (mode) {
            QuestionWordingMode.MASCULINE -> canonicalText
            QuestionWordingMode.FEMININE -> FEMININE_BY_ID[questionId] ?: canonicalText
            QuestionWordingMode.NEUTRAL -> NEUTRAL_BY_ID[questionId] ?: canonicalText
        }
    }
}
