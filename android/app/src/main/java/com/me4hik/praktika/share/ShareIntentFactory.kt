// 07.08.2026 Stage 21 Share cursor by Me4Hik START - testable Android share Intent factory
package com.me4hik.praktika.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareIntentFactory {
    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    fun fileProviderAuthority(packageName: String): String {
        return "$packageName$FILE_PROVIDER_AUTHORITY_SUFFIX"
    }

    fun createTextShareIntent(payload: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, payload)
        }
    }

    fun createFileShareIntent(
        context: Context,
        file: File,
        mimeType: String,
    ): Intent {
        val authority = fileProviderAuthority(context.packageName)
        val uri = FileProvider.getUriForFile(context, authority, file)
        return createFileShareIntent(uri, mimeType)
    }

    fun createFileShareIntent(
        contentUri: android.net.Uri,
        mimeType: String,
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", contentUri)
        }
    }

    fun createChooser(shareIntent: Intent, title: CharSequence): Intent {
        return Intent.createChooser(shareIntent, title)
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
