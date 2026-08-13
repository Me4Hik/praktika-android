package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.restore.BackupRestorePreview
import com.me4hik.praktika.data.backup.restore.BackupRestoreSelectedIdentity
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.ui.saf.InMemorySafProofStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePrepareGateTest {
    @Test
    fun emptyFolder_allowsPrepare() {
        val inspection = SlotInspectionResult(
            slotA = SlotReadResult.Missing,
            slotB = SlotReadResult.Missing,
        )
        assertTrue(RestorePrepareGate.canPrepare(inspection))
    }

    @Test
    fun nonEmptyFolder_blocksPrepare() {
        val inspection = SlotInspectionResult(
            slotA = SlotReadResult.Valid(
                slot = BackupSlotId.A,
                envelope = envelope(1L),
            ),
            slotB = SlotReadResult.Missing,
        )
        assertFalse(RestorePrepareGate.canPrepare(inspection))
    }

    private fun envelope(sequence: Long): PraktikaBackupEnvelope {
        val base = RichRestoreAcceptanceEnvelopeFactory().createSlotA()!!.envelope
        return PraktikaBackupEnvelope(
            backupSchemaVersion = base.backupSchemaVersion,
            backupSequence = sequence,
            createdAtEpochMillis = base.createdAtEpochMillis,
            sourceAppVersionCode = base.sourceAppVersionCode,
            sourceAppVersionName = base.sourceAppVersionName,
            sourceSeedVersion = base.sourceSeedVersion,
            backupChecksumSha256 = base.backupChecksumSha256,
            payload = base.payload,
        )
    }
}

class RestorePrepareOrchestratorTest {
    private val orchestrator = RestorePrepareOrchestrator(RichRestoreAcceptanceEnvelopeFactory())

    @Test
    fun prepare_emptyFolder_writesA1ThenB2() = runTest {
        val storage = InMemorySafProofStorage()
        val result = orchestrator.prepare(storage)
        assertTrue(result is RestorePrepareResult.Prepared)
        val inspection = storage.inspectSlots()
        assertTrue(RestorePrepareGate.matchesExpectedFinal(inspection))
    }

    @Test
    fun prepare_nonEmptyFolder_refusesWithoutWrite() = runTest {
        val storage = InMemorySafProofStorage()
        val preloaded = RichRestoreAcceptanceEnvelopeFactory().createSlotA()!!.envelope
        storage.writeSlot(BackupSlotId.A, ByteArray(0), preloaded)

        val result = orchestrator.prepare(storage)

        assertTrue(result is RestorePrepareResult.Failed)
        assertEquals(
            RestorePrepareFailure.FolderNotEmpty,
            (result as RestorePrepareResult.Failed).failure,
        )
    }

    @Test
    fun prepare_aWriteFails_bNotAttempted() = runTest {
        val storage = InMemorySafProofStorage().apply { failOnSlot = BackupSlotId.A }
        val result = orchestrator.prepare(storage)
        assertTrue(result is RestorePrepareResult.Failed)
        assertEquals(
            RestorePrepareFailure.AWriteFailed,
            (result as RestorePrepareResult.Failed).failure,
        )
        assertTrue(storage.inspectSlots().slotB is SlotReadResult.Missing)
    }

    @Test
    fun prepare_bWriteFails_aLeftIntact() = runTest {
        val storage = object : InMemorySafProofStorage() {
            override suspend fun writeSlot(
                slot: BackupSlotId,
                bytes: ByteArray,
                expectedEnvelope: PraktikaBackupEnvelope,
            ) = if (slot == BackupSlotId.B) {
                BackupSlotWriteResult.CreateFailed(slot)
            } else {
                super.writeSlot(slot, bytes, expectedEnvelope)
            }
        }

        val result = orchestrator.prepare(storage)

        assertTrue(result is RestorePrepareResult.Failed)
        assertEquals(
            RestorePrepareFailure.BWriteFailed,
            (result as RestorePrepareResult.Failed).failure,
        )
        val inspection = storage.inspectSlots()
        assertTrue(inspection.slotA is SlotReadResult.Valid)
        assertTrue(inspection.slotB is SlotReadResult.Missing)
    }

    @Test
    fun prepare_finalInspect_selectsB() = runTest {
        val storage = InMemorySafProofStorage()
        val result = orchestrator.prepare(storage)
        assertTrue(result is RestorePrepareResult.Prepared)
        assertEquals(BackupSlotId.B, (result as RestorePrepareResult.Prepared).latestSlot)
    }

    private fun PraktikaBackupEnvelope.copySequence(sequence: Long): PraktikaBackupEnvelope {
        return PraktikaBackupEnvelope(
            backupSchemaVersion = backupSchemaVersion,
            backupSequence = sequence,
            createdAtEpochMillis = createdAtEpochMillis,
            sourceAppVersionCode = sourceAppVersionCode,
            sourceAppVersionName = sourceAppVersionName,
            sourceSeedVersion = sourceSeedVersion,
            backupChecksumSha256 = backupChecksumSha256,
            payload = payload,
        )
    }
}

class RestoreProofActionGateTest {
    @Test
    fun prepareEnabledOnlyOnEmptyFolder() {
        assertTrue(
            RestoreProofActionGate.canPrepare(
                RestoreProofUiState.ReadyForPrepare(
                    aSummary = RestoreSlotSummary.Missing,
                    bSummary = RestoreSlotSummary.Missing,
                ),
            ),
        )
        assertFalse(
            RestoreProofActionGate.canPrepare(
                RestoreProofUiState.ReadyForRestore(
                    aSummary = RestoreSlotSummary.Valid(1L),
                    bSummary = RestoreSlotSummary.Valid(2L),
                    latestSlot = BackupSlotId.B,
                    targetEligibility = null,
                ),
            ),
        )
    }

    @Test
    fun restoreDisabledWithoutPreview() {
        assertFalse(
            RestoreProofActionGate.canRestore(
                RestoreProofUiState.ReadyForRestore(
                    aSummary = RestoreSlotSummary.Valid(1L),
                    bSummary = RestoreSlotSummary.Valid(2L),
                    latestSlot = BackupSlotId.B,
                    targetEligibility = null,
                ),
            ),
        )
    }

    @Test
    fun restoreEnabledOnlyOnEligibleFreshTarget() {
        assertTrue(
            RestoreProofActionGate.canRestore(
                RestoreProofUiState.PreviewReady(
                    preview = previewStub(),
                    aSummary = RestoreSlotSummary.Valid(1L),
                    bSummary = RestoreSlotSummary.Valid(2L),
                    targetEligibility = RestoreTargetEligibility.RestoreAvailable,
                ),
            ),
        )
        assertFalse(
            RestoreProofActionGate.canRestore(
                RestoreProofUiState.PreviewReady(
                    preview = previewStub(),
                    aSummary = RestoreSlotSummary.Valid(1L),
                    bSummary = RestoreSlotSummary.Valid(2L),
                    targetEligibility = RestoreTargetEligibility.TargetNotEmpty,
                ),
            ),
        )
    }

    @Test
    fun retryEnabledAfterFirstSuccess() {
        assertTrue(
            RestoreProofActionGate.canRetryRestore(
                RestoreProofUiState.RuntimeSynced(
                    preview = previewStub(),
                    reconcileSummary = "ReconcileNoChanges",
                ),
            ),
        )
    }

    private fun previewStub(): BackupRestorePreview {
        val envelope = RichRestoreAcceptanceEnvelopeFactory().createSlotB()!!.envelope
        return com.me4hik.praktika.data.backup.restore.BackupRestorePreviewBuilder.fromEnvelope(
            slot = BackupSlotId.B,
            envelope = envelope,
        )
    }
}

class RichRestoreAcceptanceEnvelopeFactoryTest {
    @Test
    fun slotA_notStarted_slotB_rich() {
        val factory = RichRestoreAcceptanceEnvelopeFactory()
        val a = factory.createSlotA()!!
        val b = factory.createSlotB()!!
        assertEquals(1L, a.envelope.backupSequence)
        assertEquals(2L, b.envelope.backupSequence)
        assertFalse(a.envelope.payload.practiceState.isPracticeStarted)
        assertTrue(b.envelope.payload.practiceState.isPracticeStarted)
        assertEquals(1, b.envelope.payload.answers.size)
        assertEquals(4, b.envelope.payload.occurrences.size)
        assertEquals(4, b.envelope.payload.practiceState.nextCyclePosition)
    }

    @Test
    fun slotB_hasSingleScheduledIncompleteAtCursor() {
        val payload = RichRestoreAcceptanceEnvelopeFactory().createSlotB()!!.envelope.payload
        val incomplete = payload.occurrences.filter {
            it.status == com.me4hik.praktika.data.model.QuestionOccurrenceStatus.SCHEDULED.name ||
                it.status == com.me4hik.praktika.data.model.QuestionOccurrenceStatus.AVAILABLE.name
        }
        assertEquals(1, incomplete.size)
        assertEquals(1 to 4, incomplete.single().cycleNumber to incomplete.single().cyclePosition)
        assertFalse(payload.occurrences.any { it.cycleNumber > 1 })
    }
}
