// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - symbolic harness actions
package com.me4hik.praktika.ui.acceptance

enum class DeviceAcceptanceHarnessAction {
    INSPECT_CURRENT,
    STASH_CURRENT_AS_A,
    STASH_CURRENT_AS_B,
    INSPECT_STASHED_A,
    INSPECT_STASHED_B,
    PICK_AND_INSPECT_ONCE,
    REVOKE_CURRENT_AUTH_GRANT,
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - read-only Room evidence action
    INSPECT_ROOM_STATE,
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
    ;

    companion object {
        const val INTENT_EXTRA_ACTION = "action"

        fun parseSymbolic(raw: String?): DeviceAcceptanceHarnessAction? {
            if (raw.isNullOrBlank()) {
                return null
            }
            return entries.firstOrNull { it.name == raw.trim() }
        }
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
