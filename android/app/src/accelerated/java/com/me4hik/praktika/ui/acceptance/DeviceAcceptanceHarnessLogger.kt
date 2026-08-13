// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - safe harness logger
package com.me4hik.praktika.ui.acceptance

import android.util.Log

object DeviceAcceptanceHarnessLogger {
    private const val TAG = "DeviceAcceptanceHarness"

    fun actionCompleted(action: String, resultStatus: String, reportFileName: String) {
        Log.i(TAG, "action=$action result=$resultStatus report=$reportFileName")
    }

    fun actionFailed(action: String, resultStatus: String) {
        Log.w(TAG, "action=$action result=$resultStatus")
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
