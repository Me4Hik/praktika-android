package com.me4hik.praktika.ui.saf

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

@Composable
fun SafStorageProofScreen(
    uiState: SafStorageProofUiState,
    onConnectClicked: () -> Unit,
    onInspectClicked: () -> Unit,
    onRunProofClicked: () -> Unit,
    onReloadConnectionClicked: () -> Unit,
) {
    val runEnabled = uiState is SafStorageProofUiState.ReadyEmptyFolder
    val inspectEnabled = uiState is SafStorageProofUiState.Connected ||
        uiState is SafStorageProofUiState.ReadyEmptyFolder ||
        uiState is SafStorageProofUiState.FolderNotEmpty ||
        uiState is SafStorageProofUiState.ProofPassed ||
        uiState is SafStorageProofUiState.Failure

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "SAF Storage Proof",
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
                enabled = uiState !is SafStorageProofUiState.Connecting &&
                    uiState !is SafStorageProofUiState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect backup test folder")
            }

            Button(
                onClick = onInspectClicked,
                enabled = inspectEnabled &&
                    uiState !is SafStorageProofUiState.Connecting &&
                    uiState !is SafStorageProofUiState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Inspect")
            }

            Button(
                onClick = onRunProofClicked,
                enabled = runEnabled && uiState !is SafStorageProofUiState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Run SAF storage proof")
            }

                Button(
                    onClick = onReloadConnectionClicked,
                    enabled = uiState !is SafStorageProofUiState.Connecting &&
                        uiState !is SafStorageProofUiState.Running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reload saved connection")
                }
            }
        }
    }
}

@Composable
private fun StatusBlock(uiState: SafStorageProofUiState) {
    when (uiState) {
        SafStorageProofUiState.Disconnected -> StatusText("Status: Disconnected")

        SafStorageProofUiState.Connecting -> StatusText("Status: Connecting…")

        is SafStorageProofUiState.Connected -> {
            StatusText("Status: Connected")
            StatusText("Provider: ${uiState.providerAuthority.orEmpty()}")
            StatusText("Read grant: ${uiState.readGrant}")
            StatusText("Write grant: ${uiState.writeGrant}")
            if (uiState.restoredFromHint) {
                StatusText("Persisted connection restored")
            }
        }

        SafStorageProofUiState.Inspecting -> StatusText("Status: Inspecting…")

        is SafStorageProofUiState.ReadyEmptyFolder -> {
            StatusText("Status: Ready — empty test folder")
            StatusText(uiState.aSummary.displayText(BackupSlotId.A))
            StatusText(uiState.bSummary.displayText(BackupSlotId.B))
        }

        is SafStorageProofUiState.FolderNotEmpty -> {
            StatusText("Status: TEST_FOLDER_NOT_EMPTY")
            StatusText(uiState.aSummary.displayText(BackupSlotId.A))
            StatusText(uiState.bSummary.displayText(BackupSlotId.B))
            StatusText("Choose a different empty folder.")
        }

        SafStorageProofUiState.Running -> StatusText("Status: Running proof…")

        is SafStorageProofUiState.ProofPassed -> {
            StatusText("Status: PASS")
            StatusText(uiState.aSummary.displayText(BackupSlotId.A))
            StatusText(uiState.bSummary.displayText(BackupSlotId.B))
            StatusText("Latest: ${uiState.latest.name}")
            StatusText(
                "DOCUMENT_URI_REDISCOVERY_WORKED=" +
                    if (uiState.documentUriRediscoveryWorked) "YES" else "NO",
            )
        }

        is SafStorageProofUiState.Failure -> {
            StatusText("Status: FAIL")
            StatusText("Reason: ${uiState.safeReason}")
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(4.dp))
}
