package com.me4hik.praktika.data.preferences

enum class QuestionWordingMode {
    MASCULINE,
    FEMININE,
    NEUTRAL,
    ;

    companion object {
        val DEFAULT: QuestionWordingMode = MASCULINE

        fun fromStorage(raw: String?): QuestionWordingMode {
            if (raw.isNullOrBlank()) {
                return DEFAULT
            }
            return entries.firstOrNull { it.name == raw } ?: DEFAULT
        }
    }
}
