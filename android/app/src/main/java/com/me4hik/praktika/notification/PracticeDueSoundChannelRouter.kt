package com.me4hik.praktika.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import com.me4hik.praktika.R
import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.sound.SoundAsset
import com.me4hik.praktika.sound.SoundAssetIds
import com.me4hik.praktika.sound.SoundAssetSource
import com.me4hik.praktika.sound.SoundDisplayNames
import com.me4hik.praktika.sound.SoundUriResolver

/**
 * Due-question channel routing for the V1 sound library.
 * Custom builtin channels are created lazily; [ensureBaseChannels] never creates all 25.
 */
object PracticeDueSoundChannelRouter {
    private const val CUSTOM_DUE_PREFIX = "practice_due_custom_v1_"

    fun customDueSoundChannelId(soundAssetId: String): String {
        val number = SoundAssetIds.builtinNumberOrNull(soundAssetId)
            ?: error("Not a builtin sound id: $soundAssetId")
        return CUSTOM_DUE_PREFIX + "%02d".format(number)
    }

    fun dueChannelId(
        soundEnabled: Boolean,
        selectedSoundId: String = SoundAssetIds.SYSTEM_DEFAULT,
    ): String {
        if (!soundEnabled) {
            return PracticeNotificationChannels.DUE_SILENT
        }
        val asset = BuiltinSoundCatalog.resolveOrDefault(selectedSoundId)
        return when (asset.source) {
            SoundAssetSource.SYSTEM_DEFAULT -> PracticeNotificationChannels.DUE_SOUND
            SoundAssetSource.BUILTIN -> customDueSoundChannelId(asset.id)
            SoundAssetSource.IMPORTED -> PracticeNotificationChannels.DUE_SOUND
        }
    }

    /**
     * Ensures the custom HIGH channel for [asset] exists.
     * No-op for system default / non-builtin. Does not create sibling builtin channels.
     */
    fun ensureCustomDueSoundChannel(
        context: Context,
        notificationManager: NotificationManager,
        asset: SoundAsset,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (asset.source != SoundAssetSource.BUILTIN) return
        val channelId = customDueSoundChannelId(asset.id)
        val displayName = SoundDisplayNames.forAsset(context, asset)
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_due_custom_sound_name, displayName),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(
                R.string.notification_channel_due_custom_sound_description,
                displayName,
            )
            enableVibration(true)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(SoundUriResolver.forBuiltin(context, asset), audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun ensureCustomDueSoundChannelForId(
        context: Context,
        notificationManager: NotificationManager,
        selectedSoundId: String,
    ) {
        val asset = BuiltinSoundCatalog.resolveOrDefault(selectedSoundId)
        ensureCustomDueSoundChannel(context, notificationManager, asset)
    }
}
