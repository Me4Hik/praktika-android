// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.0 physical slot safety policy
package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.slot.BackupSlotCandidate
import com.me4hik.praktika.data.backup.slot.BackupSlotSelector
import com.me4hik.praktika.data.backup.slot.BackupWritePlan
import com.me4hik.praktika.data.backup.slot.BackupWriteSequencePolicy
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult

object BackupPhysicalSlotSafetyPolicy {
    sealed interface WritePlan {
        data class Ready(
            val targetSlot: BackupSlotId,
            val nextSequence: Long,
        ) : WritePlan

        data object SequenceExhausted : WritePlan
    }

    fun classifyHardRefusal(inspection: SlotInspectionResult): BackupOutcome? {
        if (hasAmbiguousSlot(inspection)) {
            return BackupOutcome.AmbiguousSlot
        }

        if (hasValidSlot(inspection)) {
            return null
        }

        if (hasUnreadableSlot(inspection)) {
            return BackupOutcome.StorageReadFailed
        }

        if (bothSidesHavePhysicalArtifact(inspection)) {
            return when {
                inspection.slotA is SlotReadResult.Invalid &&
                    inspection.slotB is SlotReadResult.Invalid ->
                    BackupOutcome.BothSlotsInvalid

                else -> BackupOutcome.UntrustedArtifactsPresent
            }
        }

        return null
    }

    fun selectLatestValid(inspection: SlotInspectionResult): Pair<BackupSlotId, PraktikaBackupEnvelope>? {
        val candidates = listOf(
            inspection.slotA.toCandidate(BackupSlotId.A),
            inspection.slotB.toCandidate(BackupSlotId.B),
        )
        val slot = BackupSlotSelector.selectBest(candidates) ?: return null
        val envelope = inspection.resultFor(slot).envelopeOrNull() ?: return null
        return slot to envelope
    }

    fun collectValidSlots(
        inspection: SlotInspectionResult,
    ): List<Pair<BackupSlotId, PraktikaBackupEnvelope>> {
        return BackupSlotId.entries.mapNotNull { slot ->
            val envelope = inspection.resultFor(slot).envelopeOrNull() ?: return@mapNotNull null
            slot to envelope
        }
    }

    fun hasValidAndUnreadable(inspection: SlotInspectionResult): Boolean {
        if (!hasValidSlot(inspection)) {
            return false
        }
        return hasUnreadableSlot(inspection)
    }

    fun planWriteAfterSemanticDiff(inspection: SlotInspectionResult): WritePlan {
        val validSlots = collectValidSlots(inspection)
        if (validSlots.isNotEmpty()) {
            return when (val plan = BackupWriteSequencePolicy.planNextWrite(validSlots)) {
                is BackupWritePlan.Ready -> WritePlan.Ready(
                    targetSlot = plan.targetSlot,
                    nextSequence = plan.nextSequence,
                )

                BackupWritePlan.SequenceExhausted -> WritePlan.SequenceExhausted
            }
        }

        if (inspection.slotA is SlotReadResult.Missing && inspection.slotB is SlotReadResult.Missing) {
            return WritePlan.Ready(
                targetSlot = BackupSlotId.A,
                nextSequence = 1L,
            )
        }

        val singleUntrustedOppositeMissing = singleUntrustedOppositeMissingPlan(inspection)
        if (singleUntrustedOppositeMissing != null) {
            return singleUntrustedOppositeMissing
        }

        error("planWriteAfterSemanticDiff called for unsafe inspection state")
    }

    private fun singleUntrustedOppositeMissingPlan(inspection: SlotInspectionResult): WritePlan.Ready? {
        return when {
            isRecoverableUntrusted(inspection.slotA) && inspection.slotB is SlotReadResult.Missing ->
                WritePlan.Ready(
                    targetSlot = BackupSlotId.B,
                    nextSequence = 1L,
                )

            inspection.slotA is SlotReadResult.Missing && isRecoverableUntrusted(inspection.slotB) ->
                WritePlan.Ready(
                    targetSlot = BackupSlotId.A,
                    nextSequence = 1L,
                )

            else -> null
        }
    }

    private fun hasAmbiguousSlot(inspection: SlotInspectionResult): Boolean {
        return inspection.slotA is SlotReadResult.Ambiguous ||
            inspection.slotB is SlotReadResult.Ambiguous
    }

    private fun hasValidSlot(inspection: SlotInspectionResult): Boolean {
        return inspection.slotA is SlotReadResult.Valid ||
            inspection.slotB is SlotReadResult.Valid
    }

    private fun hasUnreadableSlot(inspection: SlotInspectionResult): Boolean {
        return inspection.slotA is SlotReadResult.Unreadable ||
            inspection.slotB is SlotReadResult.Unreadable
    }

    private fun bothSidesHavePhysicalArtifact(inspection: SlotInspectionResult): Boolean {
        return !isMissing(inspection.slotA) && !isMissing(inspection.slotB)
    }

    private fun isRecoverableUntrusted(result: SlotReadResult): Boolean {
        return result is SlotReadResult.Invalid || result is SlotReadResult.TooLarge
    }

    private fun isMissing(result: SlotReadResult): Boolean {
        return result is SlotReadResult.Missing
    }

    private fun SlotReadResult.envelopeOrNull(): PraktikaBackupEnvelope? {
        return (this as? SlotReadResult.Valid)?.envelope
    }

    private fun SlotReadResult.toCandidate(slotId: BackupSlotId): BackupSlotCandidate {
        val validation = when (this) {
            is SlotReadResult.Valid -> BackupFormatValidationResult.Valid(envelope)
            else -> BackupFormatValidationResult.Invalid(
                BackupFormatFailureReason.InvalidMetadata,
                "not-valid",
            )
        }
        return BackupSlotCandidate(slotId = slotId, validation = validation)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
