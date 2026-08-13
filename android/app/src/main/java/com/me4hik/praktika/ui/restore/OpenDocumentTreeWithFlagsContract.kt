// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.1 open document tree contract
package com.me4hik.praktika.ui.restore

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

data class OpenTreeResult(
    val uri: Uri,
    val flags: Int,
)

class OpenDocumentTreeWithFlagsContract :
    ActivityResultContract<Uri?, OpenTreeResult?>() {

    override fun createIntent(context: Context, input: Uri?): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            input?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): OpenTreeResult? {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return null
        }
        val uri = intent.data ?: return null
        return OpenTreeResult(uri = uri, flags = intent.flags)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
