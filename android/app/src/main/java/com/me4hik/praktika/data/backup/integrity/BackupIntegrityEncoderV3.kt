package com.me4hik.praktika.data.backup.integrity

import com.me4hik.praktika.data.backup.model.BackupMoodCheckIn
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

/**
 * V3 integrity: V2 field material unchanged (including deferEvents), then moodCheckIns appended.
 */
object BackupIntegrityEncoderV3 : BackupIntegrityEncoder {
    override fun encode(envelope: PraktikaBackupEnvelope): ByteArray {
        val v2Prefix = BackupIntegrityEncoderV2.encode(envelope)
        val moodWriter = BinaryIntegrityWriter()
        val moodCheckIns = envelope.payload.moodCheckIns.sortedWith(MOOD_CHECK_IN_ORDER)
        moodWriter.writeInt(moodCheckIns.size)
        moodCheckIns.forEach { encodeMoodCheckIn(moodWriter, it) }
        return v2Prefix + moodWriter.toByteArray()
    }

    private fun encodeMoodCheckIn(writer: BinaryIntegrityWriter, event: BackupMoodCheckIn) {
        writer.writeInt(event.cycleNumber)
        writer.writeInt(event.cyclePosition)
        writer.writeInt(event.questionId)
        writer.writeString(event.level)
        writer.writeLong(event.createdAtEpochMillis)
        writer.writeLong(event.updatedAtEpochMillis)
        writer.writeString(event.zoneId)
    }

    val MOOD_CHECK_IN_ORDER = compareBy<BackupMoodCheckIn>(
        { it.cycleNumber },
        { it.cyclePosition },
        { it.createdAtEpochMillis },
        { it.updatedAtEpochMillis },
        { it.level },
        { it.questionId },
        { it.zoneId },
    )
}
