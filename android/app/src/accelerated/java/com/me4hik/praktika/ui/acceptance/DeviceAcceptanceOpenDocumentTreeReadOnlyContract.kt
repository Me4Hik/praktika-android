// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - temporary READ-only tree picker contract
package com.me4hik.praktika.ui.acceptance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens ACTION_OPEN_DOCUMENT_TREE with temporary READ grant only.
 * Does not request WRITE or PERSISTABLE flags.
 */
class DeviceAcceptanceOpenDocumentTreeReadOnlyContract :
    ActivityResultContract<Uri?, Uri?>() {

    override fun createIntent(context: Context, input: Uri?): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            input?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return null
        }
        return intent.data
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
