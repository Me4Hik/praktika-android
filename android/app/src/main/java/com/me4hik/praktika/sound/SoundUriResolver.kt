package com.me4hik.praktika.sound

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

/**
 * Stable URI helper for bundled notification sounds.
 * Uses name-based `android.resource` paths — never numeric [R.raw] ids in the URI string.
 */
object SoundUriResolver {
    /**
     * @param resourceName raw base name without extension, e.g. `notif_practice_01`
     */
    fun forBuiltinResourceName(context: Context, resourceName: String): Uri {
        require(resourceName.isNotBlank()) { "resourceName must not be blank" }
        require(!resourceName.contains('/')) { "resourceName must be a raw base name" }
        require(!resourceName.contains('.')) { "resourceName must not include an extension" }
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath("raw")
            .appendPath(resourceName)
            .build()
    }

    fun forBuiltin(context: Context, asset: SoundAsset): Uri {
        require(asset.source == SoundAssetSource.BUILTIN) {
            "Only BUILTIN assets have bundled resource URIs"
        }
        val name = requireNotNull(asset.resourceName) { "BUILTIN asset missing resourceName" }
        return forBuiltinResourceName(context, name)
    }
}
