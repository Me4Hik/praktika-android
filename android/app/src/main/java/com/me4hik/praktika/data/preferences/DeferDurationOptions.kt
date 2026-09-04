package com.me4hik.praktika.data.preferences

object DeferDurationOptions {
    val ALLOWED_MINUTES: Set<Int> = setOf(5, 10, 15, 30)
    const val DEFAULT_MINUTES: Int = 15

    fun sanitize(minutes: Int): Int {
        return if (minutes in ALLOWED_MINUTES) minutes else DEFAULT_MINUTES
    }
}
