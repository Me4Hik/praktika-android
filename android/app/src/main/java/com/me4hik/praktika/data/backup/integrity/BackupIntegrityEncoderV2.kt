// PROMPT 111 — V2 integrity encoder (V1 byte material unchanged; deferEvents appended)
package com.me4hik.praktika.data.backup.integrity

import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupDeferEvent
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload

object BackupIntegrityEncoderV2 : BackupIntegrityEncoder {
    override fun encode(envelope: PraktikaBackupEnvelope): ByteArray {
        val writer = BinaryIntegrityWriter()

        writer.writeInt(envelope.backupSchemaVersion)
        writer.writeLong(envelope.backupSequence)
        writer.writeLong(envelope.createdAtEpochMillis)
        writer.writeInt(envelope.sourceAppVersionCode)
        writer.writeString(envelope.sourceAppVersionName)
        writer.writeInt(envelope.sourceSeedVersion)

        encodePayload(writer, envelope.payload)

        return writer.toByteArray()
    }

    private fun encodePayload(writer: BinaryIntegrityWriter, payload: PraktikaBackupPayload) {
        encodePracticeState(writer, payload.practiceState)

        val scheduleSlots = payload.scheduleSlots.sortedBy { it.slotIndex }
        writer.writeInt(scheduleSlots.size)
        scheduleSlots.forEach { encodeScheduleSlot(writer, it) }

        val occurrences = payload.occurrences.sortedWith(
            compareBy({ it.cycleNumber }, { it.cyclePosition }),
        )
        writer.writeInt(occurrences.size)
        occurrences.forEach { encodeOccurrence(writer, it) }

        val answers = payload.answers.sortedWith(
            compareBy({ it.cycleNumber }, { it.cyclePosition }),
        )
        writer.writeInt(answers.size)
        answers.forEach { encodeAnswer(writer, it) }

        val deferEvents = payload.deferEvents.sortedWith(DEFER_EVENT_ORDER)
        writer.writeInt(deferEvents.size)
        deferEvents.forEach { encodeDeferEvent(writer, it) }
    }

    private fun encodePracticeState(writer: BinaryIntegrityWriter, state: BackupPracticeState) {
        writer.writeBoolean(state.isPracticeStarted)
        writer.writeBoolean(state.isPaused)
        writer.writeNullableLong(state.practiceStartedAtEpochMillis)
        writer.writeInt(state.currentCycleNumber)
        writer.writeInt(state.nextCyclePosition)
        writer.writeNullableLong(state.lastProcessedAtEpochMillis)
        writer.writeNullableLong(state.pausedAtEpochMillis)
        writer.writeString(state.activeZoneId)
        writer.writeInt(state.seedVersion)
    }

    private fun encodeScheduleSlot(writer: BinaryIntegrityWriter, slot: BackupScheduleSlot) {
        writer.writeInt(slot.slotIndex)
        writer.writeInt(slot.timeOfDayMinutes)
    }

    private fun encodeOccurrence(writer: BinaryIntegrityWriter, occurrence: BackupOccurrence) {
        writer.writeInt(occurrence.questionId)
        writer.writeString(occurrence.questionTextSnapshot)
        writer.writeInt(occurrence.cycleNumber)
        writer.writeInt(occurrence.cyclePosition)
        writer.writeInt(occurrence.scheduleSlotIndex)
        writer.writeLong(occurrence.plannedAtEpochMillis)
        writer.writeLong(occurrence.availableUntilEpochMillis)
        writer.writeNullableLong(occurrence.openedAtEpochMillis)
        writer.writeNullableLong(occurrence.completedAtEpochMillis)
        writer.writeString(occurrence.status)
        writer.writeString(occurrence.zoneId)
    }

    private fun encodeAnswer(writer: BinaryIntegrityWriter, answer: BackupAnswer) {
        writer.writeInt(answer.cycleNumber)
        writer.writeInt(answer.cyclePosition)
        writer.writeString(answer.text)
        writer.writeLong(answer.createdAtEpochMillis)
    }

    private fun encodeDeferEvent(writer: BinaryIntegrityWriter, event: BackupDeferEvent) {
        writer.writeInt(event.cycleNumber)
        writer.writeInt(event.cyclePosition)
        writer.writeInt(event.questionId)
        writer.writeLong(event.occurredAtEpochMillis)
        writer.writeLong(event.deferredUntilEpochMillis)
        writer.writeInt(event.durationMinutes)
        writer.writeString(event.zoneId)
    }

    val DEFER_EVENT_ORDER = compareBy<BackupDeferEvent>(
        { it.cycleNumber },
        { it.cyclePosition },
        { it.occurredAtEpochMillis },
        { it.deferredUntilEpochMillis },
        { it.durationMinutes },
        { it.questionId },
        { it.zoneId },
    )
}
