package com.me4hik.praktika.sound

object SoundAssetIds {
    const val SYSTEM_DEFAULT = "system_default"

    fun builtin(number: Int): String = "builtin_%02d".format(number)

    fun isBuiltinId(id: String): Boolean = id.startsWith("builtin_")

    fun builtinNumberOrNull(id: String): Int? {
        if (!isBuiltinId(id)) return null
        val suffix = id.removePrefix("builtin_")
        return suffix.toIntOrNull()
    }
}
