// 04.08.2026 Reminder App cursor by Me4Hik START - маршруты навигации
// 05.08.2026 Question And Skip cursor by Me4Hik START - route конкретного occurrence
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - route выбранного дня
package com.me4hik.praktika.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val QUESTION_ARGUMENT = "occurrenceId"
    const val QUESTION_PATTERN = "question/{$QUESTION_ARGUMENT}"
    const val ANSWER_PATTERN = "answer/{$QUESTION_ARGUMENT}"
    const val ARCHIVE = "archive"
    const val ARCHIVE_DAY_ARGUMENT = "epochDay"
    const val ARCHIVE_DAY_PATTERN = "archive/day/{$ARCHIVE_DAY_ARGUMENT}"
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - routes архива по вопросам
    const val ARCHIVE_QUESTIONS = "archive/questions"
    const val ARCHIVE_QUESTION_ARGUMENT = "questionId"
    const val ARCHIVE_QUESTION_PATTERN = "archive/question/{$ARCHIVE_QUESTION_ARGUMENT}"
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
    // PROMPT 137 — analytics insights under Archive
    const val ARCHIVE_INSIGHTS = "archive/insights"
    // PROMPT 157 — missed drill-down under insights
    const val ARCHIVE_INSIGHTS_MISSED_WEEKDAY_ARGUMENT = "dayOfWeek"
    const val ARCHIVE_INSIGHTS_MISSED_WEEKDAY_PATTERN =
        "archive/insights/missed/weekday/{$ARCHIVE_INSIGHTS_MISSED_WEEKDAY_ARGUMENT}"
    const val ARCHIVE_INSIGHTS_MISSED_QUESTION_ARGUMENT = "questionId"
    const val ARCHIVE_INSIGHTS_MISSED_QUESTION_PATTERN =
        "archive/insights/missed/question/{$ARCHIVE_INSIGHTS_MISSED_QUESTION_ARGUMENT}"
    const val QUESTION_HISTORY = "question_history"
    const val SETTINGS = "settings"
    const val SOUND_LIBRARY = "settings/sound"

    fun question(occurrenceId: Long): String = "question/$occurrenceId"

    fun answer(occurrenceId: Long): String = "answer/$occurrenceId"

    fun archiveDay(epochDay: Long): String = "archive/day/$epochDay"

    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - helper route вопроса
    fun archiveQuestion(questionId: Int): String = "archive/question/$questionId"
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END

    // PROMPT 157 — missed drill-down helpers (DayOfWeek.name as path arg)
    fun insightsMissedWeekday(dayOfWeek: java.time.DayOfWeek): String =
        "archive/insights/missed/weekday/${dayOfWeek.name}"

    fun insightsMissedQuestion(questionId: Int): String =
        "archive/insights/missed/question/$questionId"

    fun parseInsightsMissedWeekday(dayOfWeekName: String): java.time.DayOfWeek? {
        return runCatching { java.time.DayOfWeek.valueOf(dayOfWeekName) }.getOrNull()
    }
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
// 05.08.2026 Question And Skip cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
