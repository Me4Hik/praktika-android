package com.me4hik.praktika.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Preview-only player. Never touches NotificationChannel or selectedSoundId.
 */
interface SoundPreviewPlayer {
    /** Invoked on the main thread when natural playback ends or errors out. */
    var onPlaybackEnded: (() -> Unit)?

    /** @return true if playback started. */
    fun play(context: Context, uri: Uri): Boolean

    fun stop()

    fun release()

    val isPlaying: Boolean
}

class MediaPlayerSoundPreviewPlayer : SoundPreviewPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override var onPlaybackEnded: (() -> Unit)? = null

    override val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    override fun play(context: Context, uri: Uri): Boolean {
        stopInternal(notifyEnded = false)
        return try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(context.applicationContext, uri)
            player.setOnCompletionListener {
                stopInternal(notifyEnded = true)
            }
            player.setOnErrorListener { _, _, _ ->
                stopInternal(notifyEnded = true)
                true
            }
            player.prepare()
            player.start()
            mediaPlayer = player
            true
        } catch (exception: Exception) {
            Log.w(TAG, "Preview play failed for $uri", exception)
            stopInternal(notifyEnded = false)
            false
        }
    }

    override fun stop() {
        stopInternal(notifyEnded = false)
    }

    override fun release() {
        stopInternal(notifyEnded = false)
    }

    private fun stopInternal(notifyEnded: Boolean) {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {
                // ignore
            }
            try {
                player.release()
            } catch (_: Exception) {
                // ignore
            }
        }
        if (notifyEnded) {
            val callback = onPlaybackEnded ?: return
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback()
            } else {
                mainHandler.post { callback() }
            }
        }
    }

    companion object {
        private const val TAG = "SoundPreviewPlayer"
    }
}

object SoundPreviewUris {
    fun forAsset(context: Context, asset: SoundAsset): Uri? {
        return when (asset.source) {
            SoundAssetSource.BUILTIN -> SoundUriResolver.forBuiltin(context, asset)
            SoundAssetSource.SYSTEM_DEFAULT ->
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            SoundAssetSource.IMPORTED -> null
        }
    }
}
