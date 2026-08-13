// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A restore session end reasons
package com.me4hik.praktika.data.backup.write

/**
 * End reasons for an in-memory restore↔backup suppression session.
 *
 * CANCELLED_OR_FAILED: UI_ACTIVE noncommitted teardown (dirty → current-auth catch-up).
 * COMMITTED_DEFERRED_HANDOFF: fallback finalize of COMMITTED_HANDOFF without auth success
 * (release + dirty-only old-auth catch-up; no RESTORE_CATCHUP). Normal B2B success path uses
 * runtime handoff job instead.
 */
enum class RestoreSessionEndReason {
    CANCELLED_OR_FAILED,
    COMMITTED_DEFERRED_HANDOFF,
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
