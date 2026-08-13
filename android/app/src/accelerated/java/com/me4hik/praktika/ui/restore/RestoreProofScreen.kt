package com.me4hik.praktika.ui.restore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility

@Composable
fun RestoreProofScreen(
    uiState: RestoreProofUiState,
    onConnectClicked: () -> Unit,
    onInspectClicked: () -> Unit,
    onPrepareClicked: () -> Unit,
    onPreviewClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
    onRetryRestoreClicked: () -> Unit,
    onReloadConnectionClicked: () -> Unit,
) {
    val busy = uiState is RestoreProofUiState.Connecting ||
        uiState is RestoreProofUiState.Inspecting ||
        uiState is RestoreProofUiState.Preparing ||
        uiState is RestoreProofUiState.Restoring

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "Restore Acceptance Harness",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusBlock(uiState)

                Button(
                    onClick = onConnectClicked,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Connect restore test folder")
                }

                Button(
                    onClick = onInspectClicked,
                    enabled = !busy && uiState !is RestoreProofUiState.Disconnected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Inspect backups")
                }

                Button(
                    onClick = onPrepareClicked,
                    enabled = !busy && RestoreProofActionGate.canPrepare(uiState),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Prepare rich test backup")
                }

                Button(
                    onClick = onPreviewClicked,
                    enabled = !busy && RestoreProofActionGate.canPreview(uiState),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Preview latest backup")
                }

                Button(
                    onClick = onRestoreClicked,
                    enabled = !busy && RestoreProofActionGate.canRestore(uiState),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Restore latest backup")
                }

                Button(
                    onClick = onRetryRestoreClicked,
                    enabled = !busy && RestoreProofActionGate.canRetryRestore(uiState),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retry restore")
                }

                Button(
                    onClick = onReloadConnectionClicked,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reload saved connection")
                }
            }
        }
    }
}

@Composable
private fun StatusBlock(uiState: RestoreProofUiState) {
    when (uiState) {
        RestoreProofUiState.Disconnected -> StatusText("Status: Disconnected")

        RestoreProofUiState.Connecting -> StatusText("Status: Connecting…")

        is RestoreProofUiState.Connected -> {
            StatusText("Status: Connected")
            StatusText("Provider: ${uiState.providerAuthority.orEmpty()}")
            StatusText("Read grant: ${uiState.readGrant}")
            StatusText("Write grant: ${uiState.writeGrant}")
            if (uiState.restoredFromHint) {
                StatusText("Persisted restore-test connection restored")
            }
        }

        RestoreProofUiState.Inspecting -> StatusText("Status: Inspecting…")

        is RestoreProofUiState.ReadyForPrepare -> {
            StatusText("Status: Ready for prepare (empty folder)")
            StatusText(uiState.aSummary.displayText(BackupSlotId.A))
            StatusText(uiState.bSummary.displayText(BackupSlotId.B))
        }

        RestoreProofUiState.Preparing -> StatusText("Status: Preparing acceptance backup…")

        is RestoreProofUiState.PrepareDone -> {
            StatusText("Status: Prepare complete")
            StatusText(uiState.aSummary.displayText(BackupSlotId.A))
            StatusText(uiState.bSummary.displayText(BackupSlotId.B))
            StatusText("Latest: ${uiState.latestSlot.name}")
        }

        is RestoreProofUiState.ReadyForRestore -> {
            StatusText("Status: Ready for restore inspection")
            StatusText(uiState.aSummary.displayText(BackupSlotId.A))
            StatusText(uiState.bSummary.displayText(BackupSlotId.B))
            StatusText("Latest: ${uiState.latestSlot?.name ?: "none"}")
        }

        is RestoreProofUiState.PreviewReady -> {
            StatusText("Status: Preview ready")
            StatusText("Slot: ${uiState.preview.selectedSlot.name}")
            StatusText("Sequence: ${uiState.preview.backupSequence}")
            StatusText("Started: ${uiState.preview.isPracticeStarted}")
            StatusText("Occurrences: ${uiState.preview.occurrenceCount}")
            StatusText("Answers: ${uiState.preview.answerCount}")
            StatusText("Deleted text: ${uiState.preview.deletedTextCount}")
            StatusText("Completed: ${uiState.preview.completedSlots}")
            StatusText("Target: ${eligibilityLabel(uiState.targetEligibility)}")
        }

        RestoreProofUiState.Restoring -> StatusText("Status: Restoring…")

        is RestoreProofUiState.RoundtripVerified -> {
            StatusText("Status: Roundtrip verified")
            StatusText(uiState.evidenceSummary)
        }

        is RestoreProofUiState.RuntimeSynced -> {
            StatusText("Status: Runtime synced")
            StatusText(uiState.reconcileSummary)
            uiState.verificationEvidence?.let { StatusText("Verifier: PASS ($it)") }
        }

        is RestoreProofUiState.RuntimeSyncFailed -> {
            StatusText("Status: Restore committed; runtime sync failed")
            StatusText("Verifier: PASS (${uiState.verificationEvidence})")
            StatusText("Reconcile failure: ${uiState.reconcileFailureClassName}")
            uiState.reconcileFailureMessage?.let { StatusText("Reconcile detail: $it") }
        }

        is RestoreProofUiState.IdempotenceResult -> {
            StatusText("Status: Idempotence check")
            StatusText(uiState.outcome)
        }

        is RestoreProofUiState.Failure -> {
            StatusText("Status: FAIL")
            StatusText("Reason: ${uiState.code}")
        }
    }
}

private fun eligibilityLabel(eligibility: RestoreTargetEligibility): String = when (eligibility) {
    RestoreTargetEligibility.RestoreAvailable -> "RestoreAvailable"
    RestoreTargetEligibility.TargetNotEmpty -> "TargetNotEmpty"
    is RestoreTargetEligibility.TargetUnsafe -> "TargetUnsafe(${eligibility.reason.name})"
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(4.dp))
}
