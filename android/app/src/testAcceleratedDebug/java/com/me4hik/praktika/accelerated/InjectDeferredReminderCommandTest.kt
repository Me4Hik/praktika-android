// 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik START
package com.me4hik.praktika.accelerated

import android.content.Context
import android.content.Intent
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.BoundaryAlarmPlan
import com.me4hik.praktika.notification.BoundaryEventType
import com.me4hik.praktika.notification.PracticeAlarmIdentity
import com.me4hik.praktika.notification.PracticeAlarmIntents
import com.me4hik.praktika.notification.PracticeAlarmReceiver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class InjectDeferredReminderCommandTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val wallNow = 1_700_000_000_000L
    private val elapsed = 55_000L
    private val occurrenceId = 7L
    private val plannedAt = 1_700_000_100_000L
    private val deferredUntil = 1_700_000_400_000L

    @Test
    fun availableWithDeferredUntil_dispatchesExactlyOneCanonicalBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = listOf(available(deferredUntil)),
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )

        assertEquals(InjectDeferredReminderCommand.STATUS_VALID_INTENT_DISPATCHED, result.status)
        assertEquals(1, broadcasts.size)
        assertEquals(occurrenceId, result.occurrenceId)
        assertEquals(deferredUntil, result.deferredUntilMs)
        assertEquals(BoundaryEventType.DEFERRED_REMINDER.name, result.eventType)

        val intent = broadcasts.single()
        val expectedAction = PracticeAlarmIdentity.actionFor(
            BoundaryAlarmPlan(
                occurrenceId = occurrenceId,
                eventType = BoundaryEventType.DEFERRED_REMINDER,
                triggerAtEpochMillis = deferredUntil,
                plannedAtEpochMillis = plannedAt,
            ),
        )
        assertEquals(expectedAction, intent.action)
        assertEquals(expectedAction, result.intentAction)
        assertEquals(PracticeAlarmReceiver::class.java.name, intent.component!!.className)
        assertEquals(occurrenceId, intent.getLongExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, -1L))
        assertEquals(
            BoundaryEventType.DEFERRED_REMINDER.name,
            intent.getStringExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE),
        )
        assertEquals(
            deferredUntil,
            intent.getLongExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, -1L),
        )
        assertEquals(
            plannedAt,
            intent.getLongExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, -1L),
        )
    }

    @Test
    fun actionAndExtrasMatchPracticeAlarmIntentsBuilder() {
        val ready = InjectDeferredReminderCommand.evaluate(
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            incomplete = listOf(available(deferredUntil)),
        )
        val fromCommand = InjectDeferredReminderCommand.buildCanonicalIntent(context, ready)
        val fromShared = PracticeAlarmIntents.buildReceiverIntent(
            context,
            BoundaryAlarmPlan(
                occurrenceId = occurrenceId,
                eventType = BoundaryEventType.DEFERRED_REMINDER,
                triggerAtEpochMillis = deferredUntil,
                plannedAtEpochMillis = plannedAt,
            ),
        )
        assertEquals(fromShared.action, fromCommand.action)
        assertEquals(
            fromShared.getLongExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, -1L),
            fromCommand.getLongExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, -1L),
        )
        assertEquals(
            fromShared.getStringExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE),
            fromCommand.getStringExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE),
        )
        assertEquals(
            fromShared.getLongExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, -1L),
            fromCommand.getLongExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, -1L),
        )
        assertEquals(
            fromShared.getLongExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, -1L),
            fromCommand.getLongExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, -1L),
        )
        assertEquals(fromShared.component?.className, fromCommand.component?.className)
    }

    @Test
    fun hostileAdbIdentityExtrasAreIgnored_roomIdentityWins() {
        val hostile = Intent().apply {
            putExtra("occurrence_id", 999L)
            putExtra("deferred_until", 1L)
            putExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, 999L)
            putExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, 1L)
            putExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, 2L)
            putExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE, "EXPIRY_BOUNDARY")
        }
        assertTrue(hostile.hasExtra("occurrence_id"))

        val broadcasts = mutableListOf<Intent>()
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = listOf(available(deferredUntil)),
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(InjectDeferredReminderCommand.STATUS_VALID_INTENT_DISPATCHED, result.status)
        assertEquals(occurrenceId, broadcasts.single().getLongExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, -1L))
        assertEquals(deferredUntil, broadcasts.single().getLongExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, -1L))
        assertEquals(
            BoundaryEventType.DEFERRED_REMINDER.name,
            broadcasts.single().getStringExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE),
        )
    }

    @Test
    fun wrongPackage_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = listOf(available(deferredUntil)),
            packageName = "com.me4hik.praktika",
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(InjectDeferredReminderCommand.STATUS_REJECTED_WRONG_PACKAGE, result.status)
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun noOccurrence_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = emptyList(),
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(InjectDeferredReminderCommand.STATUS_REJECTED_NO_OCCURRENCE, result.status)
        assertTrue(broadcasts.isEmpty())
        assertNull(result.dispatchedIntent)
    }

    @Test
    fun ambiguousIncomplete_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = listOf(
                available(deferredUntil),
                available(deferredUntil).copy(id = 8L),
            ),
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(InjectDeferredReminderCommand.STATUS_REJECTED_AMBIGUOUS, result.status)
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun nonAvailable_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = listOf(
                InjectDeferredReminderCommand.IncompleteOccurrence(
                    id = occurrenceId,
                    status = QuestionOccurrenceStatus.SCHEDULED,
                    plannedAtEpochMillis = plannedAt,
                    deferredUntilEpochMillis = deferredUntil,
                ),
            ),
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(InjectDeferredReminderCommand.STATUS_REJECTED_NOT_AVAILABLE, result.status)
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun noDeferredUntil_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = listOf(available(deferredUntil = null)),
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(InjectDeferredReminderCommand.STATUS_REJECTED_NO_DEFERRED_UNTIL, result.status)
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun futureDefer_stillDispatchesValid() {
        // now before deferredUntil — must NOT reject
        val futureUntil = wallNow + 300_000L
        val broadcasts = mutableListOf<Intent>()
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = listOf(available(futureUntil)),
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(InjectDeferredReminderCommand.STATUS_VALID_INTENT_DISPATCHED, result.status)
        assertEquals(1, broadcasts.size)
        assertTrue(wallNow < futureUntil)
    }

    @Test
    fun maturedDefer_stillDispatchesValid() {
        // now after deferredUntil — must NOT reject
        val pastUntil = wallNow - 60_000L
        val broadcasts = mutableListOf<Intent>()
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = listOf(available(pastUntil)),
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(InjectDeferredReminderCommand.STATUS_VALID_INTENT_DISPATCHED, result.status)
        assertEquals(1, broadcasts.size)
        assertTrue(wallNow > pastUntil)
    }

    @Test
    fun practiceAlarmReceiverRemainsNotExportedInMainManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val receiverBlock = Regex(
            """<receiver[^>]*android:name="\.notification\.PracticeAlarmReceiver"[^>]*/?>""",
        ).find(manifest)?.value
            ?: Regex(
                """<receiver[\s\S]*?android:name="\.notification\.PracticeAlarmReceiver"[\s\S]*?</receiver>""",
            ).find(manifest)?.value
        assertNotNull("PracticeAlarmReceiver declaration missing", receiverBlock)
        assertTrue(
            "PracticeAlarmReceiver must remain exported=false",
            receiverBlock!!.contains("""android:exported="false""""),
        )
        assertFalse(receiverBlock.contains("""android:exported="true""""))
    }

    @Test
    fun productionFlavorHasNoInjectDeferredReminderCommandSurface() {
        val productionRoot = File("src/production")
        assertTrue(productionRoot.isDirectory)
        val hits = productionRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .flatMap { it.readText().lineSequence() }
            .filter { line ->
                line.contains("inject_deferred_reminder") ||
                    line.contains("InjectDeferredReminderCommand")
            }
            .toList()
        assertTrue(
            "production sources must not reference inject_deferred_reminder: $hits",
            hits.isEmpty(),
        )

        val mainHits = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { it.readText().lineSequence() }
            .filter { it.contains("InjectDeferredReminderCommand") || it.contains("\"inject_deferred_reminder\"") }
            .toList()
        assertTrue(
            "main must not wire inject_deferred_reminder command: $mainHits",
            mainHits.isEmpty(),
        )

        val acceleratedProcessor = File(
            "src/accelerated/java/com/me4hik/praktika/accelerated/AcceleratedCommandProcessor.kt",
        ).readText()
        assertTrue(acceleratedProcessor.contains("InjectDeferredReminderCommand.COMMAND"))
    }

    @Test
    fun commandNameIsInjectDeferredReminder() {
        assertEquals("inject_deferred_reminder", InjectDeferredReminderCommand.COMMAND)
    }

    @Test
    fun rejectionDoesNotAttachDispatchedIntent() {
        val result = InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = emptyList(),
            packageName = InjectDeferredReminderCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { error("must not dispatch") },
        )
        assertNull(result.dispatchedIntent)
        assertNotNull(result.wallNowMs)
    }

    private fun available(deferredUntil: Long?) =
        InjectDeferredReminderCommand.IncompleteOccurrence(
            id = occurrenceId,
            status = QuestionOccurrenceStatus.AVAILABLE,
            plannedAtEpochMillis = plannedAt,
            deferredUntilEpochMillis = deferredUntil,
        )
}
// 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik END
