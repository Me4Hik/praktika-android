// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - grant revoke without BackupWriteState mutation
package com.me4hik.praktika.ui.acceptance

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

fun interface DeviceAcceptancePermissionReleaser {
    fun releasePersistableUriPermission(uri: Uri, flags: Int)
}

fun interface DeviceAcceptancePersistedGrantProbe {
    fun hasReadWriteGrant(uri: Uri): Boolean
}

enum class DeviceAcceptanceRevokeOutcome {
    NO_AUTH,
    REVOKED,
    ALREADY_ABSENT,
    RELEASE_FAILED,
}

class DeviceAcceptanceGrantRevoker(
    private val permissionReleaser: DeviceAcceptancePermissionReleaser,
    private val grantProbe: DeviceAcceptancePersistedGrantProbe,
) {
    constructor(contentResolver: ContentResolver) : this(
        permissionReleaser = DeviceAcceptancePermissionReleaser { uri, flags ->
            contentResolver.releasePersistableUriPermission(uri, flags)
        },
        grantProbe = DeviceAcceptancePersistedGrantProbe { uri ->
            contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == uri &&
                    permission.isReadPermission &&
                    permission.isWritePermission
            }
        },
    )

    fun revokeExactAuthorizedUri(authorizedUriString: String?): DeviceAcceptanceRevokeOutcome {
        if (authorizedUriString.isNullOrBlank()) {
            return DeviceAcceptanceRevokeOutcome.NO_AUTH
        }
        val uri = runCatching { Uri.parse(authorizedUriString.trim()) }.getOrNull()
            ?: return DeviceAcceptanceRevokeOutcome.RELEASE_FAILED
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return DeviceAcceptanceRevokeOutcome.RELEASE_FAILED
        }

        val hadGrant = grantProbe.hasReadWriteGrant(uri)
        if (!hadGrant) {
            return DeviceAcceptanceRevokeOutcome.ALREADY_ABSENT
        }

        return try {
            permissionReleaser.releasePersistableUriPermission(uri, RELEASE_FLAGS)
            if (grantProbe.hasReadWriteGrant(uri)) {
                DeviceAcceptanceRevokeOutcome.RELEASE_FAILED
            } else {
                DeviceAcceptanceRevokeOutcome.REVOKED
            }
        } catch (_: SecurityException) {
            if (grantProbe.hasReadWriteGrant(uri)) {
                DeviceAcceptanceRevokeOutcome.RELEASE_FAILED
            } else {
                DeviceAcceptanceRevokeOutcome.ALREADY_ABSENT
            }
        } catch (_: Exception) {
            DeviceAcceptanceRevokeOutcome.RELEASE_FAILED
        }
    }

    companion object {
        const val RELEASE_FLAGS: Int =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
