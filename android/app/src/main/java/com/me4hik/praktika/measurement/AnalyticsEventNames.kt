package com.me4hik.praktika.measurement

object AnalyticsEventNames {
    const val LANGUAGE_SELECTED = "language_selected"
    const val LANGUAGE_CHANGED = "language_changed"
    const val PRACTICE_STARTED = "practice_started"
    const val QUESTION_OPENED = "question_opened"
    const val ANSWER_SAVED = "answer_saved"
    const val QUESTION_SKIPPED = "question_skipped"
    const val QUESTION_DEFERRED = "question_deferred"
    const val MOOD_CHECKIN_SAVED = "mood_checkin_saved"
    const val NOTIFICATION_OPENED = "notification_opened"
    const val ARCHIVE_OPENED = "archive_opened"
    const val ARCHIVE_EXPORTED = "archive_exported"
    const val BACKUP_CREATED = "backup_created"
    const val BACKUP_RESTORED = "backup_restored"

    /** Diagnostics-only synthetic event; never routed to remote providers. */
    const val DEBUG_TEST_EVENT = "debug_test_event"
}
