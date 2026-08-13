package com.me4hik.praktika.ui.saf

import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssembler
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssemblyResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.metadata.BuildConfigBackupAppMetadataProvider
import com.me4hik.praktika.data.backup.metadata.SystemBackupClock
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload

data class SyntheticEnvelopeBundle(
    val envelope: PraktikaBackupEnvelope,
    val bytes: ByteArray,
)

class SyntheticBackupEnvelopeFactory(
    private val metadataFactory: BackupSnapshotMetadataFactory = BackupSnapshotMetadataFactory(
        clock = SystemBackupClock,
        appMetadataProvider = BuildConfigBackupAppMetadataProvider(),
    ),
) {
    private val payload: PraktikaBackupPayload by lazy { createHarmlessPayload() }

    fun create(sequence: Long): SyntheticEnvelopeBundle? {
        val metadata = metadataFactory.capture()
        return when (
            val assembled = BackupEnvelopeAssembler.assemble(
                payload = payload,
                backupSequence = sequence,
                metadata = metadata,
            )
        ) {
            is BackupEnvelopeAssemblyResult.Success -> {
                val envelope = assembled.envelope
                SyntheticEnvelopeBundle(
                    envelope = envelope,
                    bytes = BackupJsonEncoder.encodeToUtf8Bytes(envelope),
                )
            }

            else -> null
        }
    }

    private fun createHarmlessPayload(): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = BackupPracticeState(
                isPracticeStarted = false,
                isPaused = false,
                practiceStartedAtEpochMillis = null,
                currentCycleNumber = 0,
                nextCyclePosition = 0,
                lastProcessedAtEpochMillis = null,
                pausedAtEpochMillis = null,
                activeZoneId = "UTC",
                seedVersion = 1,
            ),
            scheduleSlots = emptyList(),
            occurrences = emptyList(),
            answers = emptyList(),
        )
    }
}
