package com.me4hik.praktika.data.mood

import com.me4hik.praktika.data.model.MoodLevel

data class MoodCopy(
    val level: MoodLevel,
    val emoji: String,
    val title: String,
    val explanation: String,
)
