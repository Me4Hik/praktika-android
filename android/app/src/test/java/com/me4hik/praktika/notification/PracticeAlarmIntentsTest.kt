// 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik START
package com.me4hik.praktika.notification

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PracticeAlarmIntentsTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun buildReceiverIntent_matchesIdentityAndExtrasContract() {
        val plan = BoundaryAlarmPlan(
            occurrenceId = 11L,
            eventType = BoundaryEventType.DEFERRED_REMINDER,
            triggerAtEpochMillis = 1_700_000_500_000L,
            plannedAtEpochMillis = 1_700_000_000_000L,
        )
        val intent = PracticeAlarmIntents.buildReceiverIntent(context, plan)
        assertEquals(PracticeAlarmIdentity.actionFor(plan), intent.action)
        assertEquals(PracticeAlarmReceiver::class.java.name, intent.component!!.className)
        assertEquals(11L, intent.getLongExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, -1L))
        assertEquals(
            BoundaryEventType.DEFERRED_REMINDER.name,
            intent.getStringExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE),
        )
        assertEquals(
            1_700_000_500_000L,
            intent.getLongExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, -1L),
        )
        assertEquals(
            1_700_000_000_000L,
            intent.getLongExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, -1L),
        )
    }
}
// 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik END
