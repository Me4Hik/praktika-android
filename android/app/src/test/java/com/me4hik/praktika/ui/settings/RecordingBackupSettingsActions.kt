package com.me4hik.praktika.ui.settings

import com.me4hik.praktika.data.backup.settings.BackupNowResult
import com.me4hik.praktika.data.backup.settings.BackupReconnectResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsAbandonResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsDisableResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsOperationalState
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupCommitResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupInspectResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupVerifyResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class RecordingBackupSettingsActions(
    initialStatus: BackupSettingsOperationalState = BackupSettingsOperationalState.NotConfigured,
) : BackupSettingsActions {
    private val status = MutableStateFlow(initialStatus)

    var inspectResult: BackupSettingsSetupInspectResult =
        BackupSettingsSetupInspectResult.Unavailable
    var verifyResult: BackupSettingsSetupVerifyResult = BackupSettingsSetupVerifyResult.NoSession
    var commitResult: BackupSettingsSetupCommitResult = BackupSettingsSetupCommitResult.ReceiptMissing
    var abandonResult: BackupSettingsAbandonResult = BackupSettingsAbandonResult.Success
    var reconnectResult: BackupReconnectResult = BackupReconnectResult.NotConfigured
    var backupNowResult: BackupNowResult = BackupNowResult.NotConfigured
    var disableResult: BackupSettingsDisableResult = BackupSettingsDisableResult.Success

    var verifyResultProvider: (() -> BackupSettingsSetupVerifyResult)? = null
    var commitResultProvider: (() -> BackupSettingsSetupCommitResult)? = null
    var inspectThrows: Boolean = false
    var disableDelayMs: Long = 0L
    var disableGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val failStatusFlow = AtomicBoolean(false)

    val abandonCount = AtomicInteger(0)
    val requestAbandonCount = AtomicInteger(0)
    val inspectCount = AtomicInteger(0)
    val verifyCount = AtomicInteger(0)
    val commitCount = AtomicInteger(0)
    val disableCount = AtomicInteger(0)

    fun emitStatus(value: BackupSettingsOperationalState) {
        status.value = value
    }

    override fun observeOperationalStatus(): Flow<BackupSettingsOperationalState> {
        return status
            .onStart {
                if (failStatusFlow.get()) {
                    error("status-flow-failure")
                }
            }
            .map { value ->
                if (failStatusFlow.get()) {
                    error("status-flow-failure")
                }
                value
            }
    }

    override suspend fun inspectCandidate(
        uriString: String,
        grantFlags: Int,
    ): BackupSettingsSetupInspectResult {
        inspectCount.incrementAndGet()
        if (inspectThrows) {
            error("inspect-boom")
        }
        return inspectResult
    }

    override suspend fun verifyCurrentCandidate(): BackupSettingsSetupVerifyResult {
        verifyCount.incrementAndGet()
        return verifyResultProvider?.invoke() ?: verifyResult
    }

    override suspend fun commitVerifiedCandidate(): BackupSettingsSetupCommitResult {
        commitCount.incrementAndGet()
        return commitResultProvider?.invoke() ?: commitResult
    }

    override suspend fun abandonSetupSession(): BackupSettingsAbandonResult {
        abandonCount.incrementAndGet()
        return abandonResult
    }

    override fun requestAbandonSetupSession() {
        requestAbandonCount.incrementAndGet()
    }

    override suspend fun reconnectFolderAccess(
        selectedUriString: String,
        grantFlags: Int,
    ): BackupReconnectResult = reconnectResult

    override suspend fun runBackupNow(): BackupNowResult = backupNowResult

    override suspend fun disableAutomaticBackup(): BackupSettingsDisableResult {
        disableCount.incrementAndGet()
        disableGate?.await()
        if (disableDelayMs > 0) {
            delay(disableDelayMs)
        }
        return disableResult
    }
}