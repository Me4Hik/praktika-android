package com.me4hik.praktika.sound

/**
 * V1 sound-library asset. [id] is the stable preference/channel key;
 * [resourceName] is the `res/raw` base name (no extension) for builtins only.
 * Display labels stay separate from resource/channel identities.
 */
data class SoundAsset(
    val id: String,
    val source: SoundAssetSource,
    /** Raw resource base name, e.g. `notif_practice_01`. Null for system default. */
    val resourceName: String?,
    /** Human-facing number for builtins (1..28); null for system default. */
    val displayNumber: Int?,
    val deletable: Boolean,
) {
    val isBuiltin: Boolean get() = source == SoundAssetSource.BUILTIN
    val isSystemDefault: Boolean get() = source == SoundAssetSource.SYSTEM_DEFAULT
}
