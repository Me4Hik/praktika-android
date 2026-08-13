// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B restore runtime bridge
package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.CommittedHandoffStartResult
import com.me4hik.praktika.data.backup.write.RestoreSessionEndReason

/**
 * Narrow ViewModel-facing bridge for restore session + committed handoff.
 * No URI / DataStore / CoroutineScope / SAF types.
 */
interface RestoreBackupRuntimeBridge {
    fun beginRestoreSession()

    fun endNonCommittedSession()

    fun startCommittedHandoff(): CommittedHandoffStartResult

    fun isRestoreSessionActive(): Boolean
}

/**
 * Back-compat alias used by B2A call sites / tests.
 */
typealias RestoreBackupSessionController = RestoreBackupRuntimeBridge

class AuthorizedBackupRestoreSessionController(
    private val service: AuthorizedBackupService,
) : RestoreBackupRuntimeBridge {
    override fun beginRestoreSession() {
        service.beginRestoreSession()
    }

    override fun endNonCommittedSession() {
        service.endRestoreSession(RestoreSessionEndReason.CANCELLED_OR_FAILED)
    }

    /** @deprecated Prefer [endNonCommittedSession]; maps CANCELLED_OR_FAILED only. */
    fun endRestoreSession(reason: RestoreSessionEndReason) {
        service.endRestoreSession(reason)
    }

    override fun startCommittedHandoff(): CommittedHandoffStartResult {
        return service.startCommittedHandoff()
    }

    override fun isRestoreSessionActive(): Boolean {
        return service.isRestoreSessionActive()
    }
}

/** Test / inert no-op controller when backup runtime is not wired. */
object NoOpRestoreBackupSessionController : RestoreBackupRuntimeBridge {
    override fun beginRestoreSession() = Unit

    override fun endNonCommittedSession() = Unit

    override fun startCommittedHandoff(): CommittedHandoffStartResult =
        CommittedHandoffStartResult.Unavailable

    override fun isRestoreSessionActive(): Boolean = false
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
