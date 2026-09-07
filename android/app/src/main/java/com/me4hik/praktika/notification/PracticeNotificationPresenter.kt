// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - platform notification boundary
package com.me4hik.praktika.notification

interface PracticeNotificationPresenter {
    fun ensureChannelsCreated()

    /**
     * Removes orphan `practice_due_custom_v1_*` channels not needed for [selectedSoundId],
     * optionally preserving [additionalKeepChannelIds] (active QUESTION hosting protection
     * when this sync did not replace/cancel that notification).
     * Default no-op for test doubles; production presenter performs the prune.
     */
    fun pruneCustomDueSoundChannels(
        selectedSoundId: String,
        additionalKeepChannelIds: Set<String> = emptySet(),
    ) = Unit

    fun findActivePracticeNotificationOccurrenceId(): Long?

    fun findActivePracticeNotificationKind(): PracticeNotificationKind?

    /** Channel id of the preferred active practice notification, if any. */
    fun findActivePracticeNotificationChannelId(): String? = null

    /**
     * Posts/replaces the practice notification.
     * @return true if notify completed successfully; false if skipped or failed.
     */
    fun showNotification(plan: NotificationShowPlan): Boolean

    fun cancelCurrentPracticeNotification()

    fun cancelLegacyPracticeNotifications(
        currentOccurrenceId: Long? = null,
        syncReason: String? = null,
    )

    fun cancelAllPracticeNotifications()
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
