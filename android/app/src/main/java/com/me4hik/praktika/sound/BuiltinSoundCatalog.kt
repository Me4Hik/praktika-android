package com.me4hik.praktika.sound

/**
 * Compile-time catalog: system default + curated bundled practice sounds.
 * Removed from V1 full set: 1, 2, 4, 5, 6, 7, 9, 12, 14, 15, 17, 19, 20, 21, 22
 * (plus historical gaps 11/16/18 that never shipped).
 */
object BuiltinSoundCatalog {
    /** Source OGG numbers present in the curated library. */
    val BUILTIN_NUMBERS: List<Int> = listOf(
        3, 8, 10, 13, 23, 24, 25, 26, 27, 28,
    )

    val SYSTEM_DEFAULT: SoundAsset = SoundAsset(
        id = SoundAssetIds.SYSTEM_DEFAULT,
        source = SoundAssetSource.SYSTEM_DEFAULT,
        resourceName = null,
        displayNumber = null,
        deletable = false,
    )

    val BUILTINS: List<SoundAsset> = BUILTIN_NUMBERS.map { number ->
        SoundAsset(
            id = SoundAssetIds.builtin(number),
            source = SoundAssetSource.BUILTIN,
            resourceName = "notif_practice_%02d".format(number),
            displayNumber = number,
            deletable = false,
        )
    }

    private val byId: Map<String, SoundAsset> =
        (listOf(SYSTEM_DEFAULT) + BUILTINS).associateBy { it.id }

    fun all(): List<SoundAsset> = listOf(SYSTEM_DEFAULT) + BUILTINS

    fun builtins(): List<SoundAsset> = BUILTINS

    fun findById(id: String): SoundAsset? = byId[id]

    /** Unknown / blank / unsupported ids resolve to [SYSTEM_DEFAULT]. */
    fun resolveOrDefault(id: String?): SoundAsset {
        if (id.isNullOrBlank()) return SYSTEM_DEFAULT
        return byId[id] ?: SYSTEM_DEFAULT
    }

    fun visible(hiddenBuiltinIds: Set<String>): List<SoundAsset> {
        return all().filter { asset ->
            when (asset.source) {
                SoundAssetSource.SYSTEM_DEFAULT -> true
                SoundAssetSource.BUILTIN -> asset.id !in hiddenBuiltinIds
                SoundAssetSource.IMPORTED -> false
            }
        }
    }

    fun containsBuiltinId(id: String): Boolean =
        findById(id)?.source == SoundAssetSource.BUILTIN
}
