// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - portable JSON backup encoder
// OrderedJsonWriter emits the frozen v1 key order contract. JSONStringer fluent chaining
// is incompatible between Android compile SDK and Maven test runtime (Prompt 144 class).
package com.me4hik.praktika.data.backup.codec

import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import java.nio.charset.StandardCharsets

object BackupJsonEncoder {
    fun encodeToUtf8Bytes(envelope: PraktikaBackupEnvelope): ByteArray {
        return encodeToJsonString(envelope).toByteArray(StandardCharsets.UTF_8)
    }

    fun encodeToJsonString(envelope: PraktikaBackupEnvelope): String {
        val payload = envelope.payload
        val scheduleSlots = payload.scheduleSlots.sortedBy { it.slotIndex }
        val occurrences = payload.occurrences.sortedWith(
            compareBy({ it.cycleNumber }, { it.cyclePosition }),
        )
        val answers = payload.answers.sortedWith(
            compareBy({ it.cycleNumber }, { it.cyclePosition }),
        )

        val writer = OrderedJsonWriter()
        writer.beginObject()
        writer.key("backupSchemaVersion")
        writer.value(envelope.backupSchemaVersion)
        writer.key("backupSequence")
        writer.value(envelope.backupSequence)
        writer.key("createdAtEpochMillis")
        writer.value(envelope.createdAtEpochMillis)
        writer.key("sourceAppVersionCode")
        writer.value(envelope.sourceAppVersionCode)
        writer.key("sourceAppVersionName")
        writer.value(envelope.sourceAppVersionName)
        writer.key("sourceSeedVersion")
        writer.value(envelope.sourceSeedVersion)
        writer.key("backupChecksumSha256")
        writer.value(envelope.backupChecksumSha256)
        writer.key("payload")
        writer.rawNestedJson(
            encodePayload(payload.practiceState, scheduleSlots, occurrences, answers),
        )
        writer.endObject()
        return writer.toJsonString()
    }

    private fun encodePayload(
        practiceState: BackupPracticeState,
        scheduleSlots: List<BackupScheduleSlot>,
        occurrences: List<BackupOccurrence>,
        answers: List<BackupAnswer>,
    ): String {
        val writer = OrderedJsonWriter()
        writer.beginObject()
        writer.key("practiceState")
        writer.rawNestedJson(encodePracticeState(practiceState))
        writer.key("scheduleSlots")
        writer.rawNestedJson(encodeScheduleSlots(scheduleSlots))
        writer.key("occurrences")
        writer.rawNestedJson(encodeOccurrences(occurrences))
        writer.key("answers")
        writer.rawNestedJson(encodeAnswers(answers))
        writer.endObject()
        return writer.toJsonString()
    }

    private fun encodePracticeState(state: BackupPracticeState): String {
        val writer = OrderedJsonWriter()
        writer.beginObject()
        writer.key("isPracticeStarted")
        writer.value(state.isPracticeStarted)
        writer.key("isPaused")
        writer.value(state.isPaused)
        writer.key("practiceStartedAtEpochMillis")
        writeNullableLong(writer, state.practiceStartedAtEpochMillis)
        writer.key("currentCycleNumber")
        writer.value(state.currentCycleNumber)
        writer.key("nextCyclePosition")
        writer.value(state.nextCyclePosition)
        writer.key("lastProcessedAtEpochMillis")
        writeNullableLong(writer, state.lastProcessedAtEpochMillis)
        writer.key("pausedAtEpochMillis")
        writeNullableLong(writer, state.pausedAtEpochMillis)
        writer.key("activeZoneId")
        writer.value(state.activeZoneId)
        writer.key("seedVersion")
        writer.value(state.seedVersion)
        writer.endObject()
        return writer.toJsonString()
    }

    private fun encodeScheduleSlots(slots: List<BackupScheduleSlot>): String {
        val writer = OrderedJsonWriter()
        writer.beginArray()
        slots.forEach { slot ->
            val slotWriter = OrderedJsonWriter()
            slotWriter.beginObject()
            slotWriter.key("slotIndex")
            slotWriter.value(slot.slotIndex)
            slotWriter.key("timeOfDayMinutes")
            slotWriter.value(slot.timeOfDayMinutes)
            slotWriter.endObject()
            writer.rawNestedJson(slotWriter.toJsonString())
        }
        writer.endArray()
        return writer.toJsonString()
    }

    private fun encodeOccurrences(occurrences: List<BackupOccurrence>): String {
        val writer = OrderedJsonWriter()
        writer.beginArray()
        occurrences.forEach { occurrence ->
            val occurrenceWriter = OrderedJsonWriter()
            occurrenceWriter.beginObject()
            occurrenceWriter.key("questionId")
            occurrenceWriter.value(occurrence.questionId)
            occurrenceWriter.key("questionTextSnapshot")
            occurrenceWriter.value(occurrence.questionTextSnapshot)
            occurrenceWriter.key("cycleNumber")
            occurrenceWriter.value(occurrence.cycleNumber)
            occurrenceWriter.key("cyclePosition")
            occurrenceWriter.value(occurrence.cyclePosition)
            occurrenceWriter.key("scheduleSlotIndex")
            occurrenceWriter.value(occurrence.scheduleSlotIndex)
            occurrenceWriter.key("plannedAtEpochMillis")
            occurrenceWriter.value(occurrence.plannedAtEpochMillis)
            occurrenceWriter.key("availableUntilEpochMillis")
            occurrenceWriter.value(occurrence.availableUntilEpochMillis)
            occurrenceWriter.key("openedAtEpochMillis")
            writeNullableLong(occurrenceWriter, occurrence.openedAtEpochMillis)
            occurrenceWriter.key("completedAtEpochMillis")
            writeNullableLong(occurrenceWriter, occurrence.completedAtEpochMillis)
            occurrenceWriter.key("deferredUntilEpochMillis")
            writeNullableLong(occurrenceWriter, occurrence.deferredUntilEpochMillis)
            occurrenceWriter.key("status")
            occurrenceWriter.value(occurrence.status)
            occurrenceWriter.key("zoneId")
            occurrenceWriter.value(occurrence.zoneId)
            occurrenceWriter.endObject()
            writer.rawNestedJson(occurrenceWriter.toJsonString())
        }
        writer.endArray()
        return writer.toJsonString()
    }

    private fun encodeAnswers(answers: List<BackupAnswer>): String {
        val writer = OrderedJsonWriter()
        writer.beginArray()
        answers.forEach { answer ->
            val answerWriter = OrderedJsonWriter()
            answerWriter.beginObject()
            answerWriter.key("cycleNumber")
            answerWriter.value(answer.cycleNumber)
            answerWriter.key("cyclePosition")
            answerWriter.value(answer.cyclePosition)
            answerWriter.key("text")
            answerWriter.value(answer.text)
            answerWriter.key("createdAtEpochMillis")
            answerWriter.value(answer.createdAtEpochMillis)
            answerWriter.endObject()
            writer.rawNestedJson(answerWriter.toJsonString())
        }
        writer.endArray()
        return writer.toJsonString()
    }

    private fun writeNullableLong(writer: OrderedJsonWriter, value: Long?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
