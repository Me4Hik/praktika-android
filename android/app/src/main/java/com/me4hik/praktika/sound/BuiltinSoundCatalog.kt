package com.me4hik.praktika.sound

/**
 * Compile-time catalog: system default + 25 bundled practice sounds.
 * Gaps 11/16/18 are intentional (no source files).
 */
object BuiltinSoundCatalog {
    /** Source OGG numbers present in the V1 library. */
    val BUILTIN_NUMBERS: List<Int> = listOf(
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
        12, 13, 14, 15, 17,
        19, 20, 21, 22, 23, 24, 25, 26, 27, 28,
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
