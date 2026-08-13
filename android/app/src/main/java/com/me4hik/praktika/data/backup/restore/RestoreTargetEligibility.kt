// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator
package com.me4hik.praktika.data.backup.restore

sealed interface RestoreTargetEligibility {
    data object RestoreAvailable : RestoreTargetEligibility

    data object TargetNotEmpty : RestoreTargetEligibility

    data class TargetUnsafe(
        val reason: TargetDatabaseUnsafeReason,
    ) : RestoreTargetEligibility
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
