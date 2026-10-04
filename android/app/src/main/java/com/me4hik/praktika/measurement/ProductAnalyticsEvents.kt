package com.me4hik.praktika.measurement

object ProductAnalyticsEvents {
    fun languageSelected(language: String): AnalyticsEvent =
        AnalyticsEvent(
            name = AnalyticsEventNames.LANGUAGE_SELECTED,
            params = SafeAnalyticsParams.builder()
                .language(language)
                .source(SafeAnalyticsParams.Sources.CHOOSER)
                .build(),
        )

    fun languageChanged(language: String, previousLanguage: String): AnalyticsEvent =
        AnalyticsEvent(
            name = AnalyticsEventNames.LANGUAGE_CHANGED,
            params = SafeAnalyticsParams.builder()
                .language(language)
                .previousLanguage(previousLanguage)
                .source(SafeAnalyticsParams.Sources.SETTINGS)
                .build(),
        )

    fun practiceStarted(flavor: String): AnalyticsEvent =
        AnalyticsEvent(
            name = AnalyticsEventNames.PRACTICE_STARTED,
            params = SafeAnalyticsParams.builder()
                .flavor(flavor)
                .build(),
        )

    fun questionOpened(questionId: Int?, source: String): AnalyticsEvent {
        val builder = SafeAnalyticsParams.builder().source(source)
        if (questionId != null) {
            builder.questionId(questionId)
        }
        return AnalyticsEvent(
            name = AnalyticsEventNames.QUESTION_OPENED,
            params = builder.build(),
        )
    }

    fun answerSaved(questionId: Int?): AnalyticsEvent {
        val builder = SafeAnalyticsParams.builder()
        if (questionId != null) {
            builder.questionId(questionId)
        }
        return AnalyticsEvent(
            name = AnalyticsEventNames.ANSWER_SAVED,
            params = builder.build(),
        )
    }

    fun questionSkipped(questionId: Int?): AnalyticsEvent {
        val builder = SafeAnalyticsParams.builder()
        if (questionId != null) {
            builder.questionId(questionId)
        }
        return AnalyticsEvent(
            name = AnalyticsEventNames.QUESTION_SKIPPED,
            params = builder.build(),
        )
    }

    fun questionDeferred(questionId: Int?, durationMinutes: Int): AnalyticsEvent {
        val builder = SafeAnalyticsParams.builder().durationMinutes(durationMinutes)
        if (questionId != null) {
            builder.questionId(questionId)
        }
        return AnalyticsEvent(
            name = AnalyticsEventNames.QUESTION_DEFERRED,
            params = builder.build(),
        )
    }

    /** Fact-only mood event. Mood level must never be attached here. */
    fun moodCheckinSaved(): AnalyticsEvent =
        AnalyticsEvent(name = AnalyticsEventNames.MOOD_CHECKIN_SAVED)

    fun notificationOpened(questionId: Int?): AnalyticsEvent {
        val builder = SafeAnalyticsParams.builder()
            .source(SafeAnalyticsParams.Sources.NOTIFICATION)
        if (questionId != null) {
            builder.questionId(questionId)
        }
        return AnalyticsEvent(
            name = AnalyticsEventNames.NOTIFICATION_OPENED,
            params = builder.build(),
        )
    }

    fun archiveOpened(): AnalyticsEvent =
        AnalyticsEvent(name = AnalyticsEventNames.ARCHIVE_OPENED)

    fun archiveExported(format: String): AnalyticsEvent =
        AnalyticsEvent(
            name = AnalyticsEventNames.ARCHIVE_EXPORTED,
            params = SafeAnalyticsParams.builder()
                .exportFormat(format)
                .build(),
        )

    fun backupCreated(): AnalyticsEvent =
        AnalyticsEvent(
            name = AnalyticsEventNames.BACKUP_CREATED,
            params = SafeAnalyticsParams.builder()
                .result("written")
                .build(),
        )

    fun backupRestored(source: String): AnalyticsEvent =
        AnalyticsEvent(
            name = AnalyticsEventNames.BACKUP_RESTORED,
            params = SafeAnalyticsParams.builder()
                .source(source)
                .build(),
        )

    fun debugTestEvent(): AnalyticsEvent =
        AnalyticsEvent(
            name = AnalyticsEventNames.DEBUG_TEST_EVENT,
            params = SafeAnalyticsParams.builder()
                .source(SafeAnalyticsParams.Sources.DIAGNOSTICS)
                .build(),
            debugOnly = true,
        )
}
