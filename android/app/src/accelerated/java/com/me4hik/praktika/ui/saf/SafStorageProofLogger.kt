package com.me4hik.praktika.ui.saf

import android.util.Log

object SafStorageProofLogger {
    private const val TAG = "PraktikaSafProof"

    fun connectionSuccess(providerAuthority: String?) {
        Log.d(TAG, "SAF_PROOF connection=success providerAuthority=${providerAuthority.orEmpty()}")
    }

    fun connectionFailure(failureCode: String) {
        Log.d(TAG, "SAF_PROOF connection=failure failureCode=$failureCode")
    }

    fun slotState(slot: String, summary: String) {
        Log.d(TAG, "slot=$slot state=$summary")
    }

    fun proofStep(step: String, result: String) {
        Log.d(TAG, "proofStep=$step result=$result")
    }

    fun proofLatest(slot: String) {
        Log.d(TAG, "latest=$slot")
    }

    fun proofFailure(failureCode: String) {
        Log.d(TAG, "failureCode=$failureCode")
    }

    fun documentUriRediscoveryWorked(yes: Boolean) {
        Log.d(TAG, "DOCUMENT_URI_REDISCOVERY_WORKED=${if (yes) "YES" else "NO"}")
    }

    fun restoredConnection(providerAuthority: String?) {
        Log.d(
            TAG,
            "SAF_PROOF restoredConnection=success providerAuthority=${providerAuthority.orEmpty()}",
        )
    }
}
