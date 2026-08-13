// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B committed handoff start result
package com.me4hik.praktika.data.backup.write

/**
 * Synchronous result of claiming committed-restore handoff ownership.
 * No URI / exception text.
 */
sealed interface CommittedHandoffStartResult {
    data object Started : CommittedHandoffStartResult

    data object AlreadyStarted : CommittedHandoffStartResult

    data object Unavailable : CommittedHandoffStartResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
