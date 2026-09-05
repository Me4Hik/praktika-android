package com.me4hik.praktika.sound

import android.content.Context
import com.me4hik.praktika.R

object SoundDisplayNames {
    fun forAsset(context: Context, asset: SoundAsset): String {
        return when (asset.source) {
            SoundAssetSource.SYSTEM_DEFAULT ->
                context.getString(R.string.notif_sound_display_system)
            SoundAssetSource.BUILTIN -> {
                val number = asset.displayNumber
                    ?: SoundAssetIds.builtinNumberOrNull(asset.id)
                    ?: 0
                context.getString(R.string.notif_sound_display_numbered, number)
            }
            SoundAssetSource.IMPORTED -> asset.id
        }
    }
}
