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
 * Custom builtin channels are created lazily; base channel ensure never creates all builtins.
 * Orphan custom channels are pruned via [pruneOrphanCustomDueSoundChannels].
 */
object PracticeDueSoundChannelRouter {
    const val CUSTOM_DUE_PREFIX = "practice_due_custom_v1_"

    fun customDueSoundChannelId(soundAssetId: String): String {
        val number = SoundAssetIds.builtinNumberOrNull(soundAssetId)
            ?: error("Not a builtin sound id: $soundAssetId")
        return CUSTOM_DUE_PREFIX + "%02d".format(number)
    }

    fun isCustomDueSoundChannelId(channelId: String): Boolean =
        channelId.startsWith(CUSTOM_DUE_PREFIX)

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

    /**
     * Deletes app-owned orphan custom due-sound channels for the current scheme.
     *
     * Keeps the custom channel for the effective selected builtin (if any) plus any
     * [additionalKeepChannelIds] (e.g. APP_START hosting channel of an active QUESTION).
     * Never touches base/legacy channels or non-prefixed ids. Idempotent.
     */
    fun pruneOrphanCustomDueSoundChannels(
        notificationManager: NotificationManager,
        selectedSoundId: String,
        additionalKeepChannelIds: Set<String> = emptySet(),
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val resolved = BuiltinSoundCatalog.resolveOrDefault(selectedSoundId)
        val selectedKeepChannelId = when (resolved.source) {
            SoundAssetSource.BUILTIN -> customDueSoundChannelId(resolved.id)
            SoundAssetSource.SYSTEM_DEFAULT,
            SoundAssetSource.IMPORTED,
            -> null
        }
        val keepIds = buildSet {
            selectedKeepChannelId?.let { add(it) }
            additionalKeepChannelIds
                .asSequence()
                .filter { isCustomDueSoundChannelId(it) }
                .forEach { add(it) }
        }
        notificationManager.notificationChannels
            .asSequence()
            .map { it.id }
            .filter { isCustomDueSoundChannelId(it) }
            .filter { it !in keepIds }
            .forEach { channelId ->
                notificationManager.deleteNotificationChannel(channelId)
            }
    }
}
