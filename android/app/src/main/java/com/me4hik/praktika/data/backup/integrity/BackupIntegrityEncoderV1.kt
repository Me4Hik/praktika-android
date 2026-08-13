// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - frozen V1 binary integrity encoding
package com.me4hik.praktika.data.backup.integrity

import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

object BackupIntegrityEncoderV1 : BackupIntegrityEncoder {
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

    private fun encodePayload(writer: BinaryIntegrityWriter, payload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload) {
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
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
