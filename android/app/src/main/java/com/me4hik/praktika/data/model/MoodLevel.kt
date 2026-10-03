package com.me4hik.praktika.data.model

enum class MoodLevel {
    VERY_LOW,
    LOW,
    NEUTRAL,
    GOOD,
    GREAT,
    ;

    companion object {
        fun fromStorage(raw: String): MoodLevel {
            return try {
                valueOf(raw)
            } catch (exception: IllegalArgumentException) {
                throw IllegalArgumentException("Unknown MoodLevel value: $raw", exception)
            }
        }
    }
}
