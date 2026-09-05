package com.me4hik.praktika.notification

import android.app.NotificationManager
import android.content.Context
import android.content.ContentResolver
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.sound.SoundAssetIds
import com.me4hik.praktika.sound.SoundUriResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PracticeDueSoundChannelRouterTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notificationChannels
            .map { it.id }
            .forEach { notificationManager.deleteNotificationChannel(it) }
    }

    @Test
    fun channelId_mapsBuiltin01ToCustomV1() {
        assertEquals(
            "practice_due_custom_v1_01",
            PracticeDueSoundChannelRouter.customDueSoundChannelId(SoundAssetIds.builtin(1)),
        )
        assertEquals(
            "practice_due_custom_v1_28",
            PracticeDueSoundChannelRouter.customDueSoundChannelId(SoundAssetIds.builtin(28)),
        )
    }

    @Test
    fun dueChannelId_systemDefaultUsesPracticeDueSound() {
        assertEquals(
            PracticeNotificationChannels.DUE_SOUND,
            PracticeDueSoundChannelRouter.dueChannelId(
                soundEnabled = true,
                selectedSoundId = SoundAssetIds.SYSTEM_DEFAULT,
            ),
        )
    }

    @Test
    fun dueChannelId_disabledUsesSilent() {
        assertEquals(
            PracticeNotificationChannels.DUE_SILENT,
            PracticeDueSoundChannelRouter.dueChannelId(
                soundEnabled = false,
                selectedSoundId = SoundAssetIds.builtin(1),
            ),
        )
    }

    @Test
    fun dueChannelId_builtinUsesCustomChannel() {
        assertEquals(
            "practice_due_custom_v1_12",
            PracticeDueSoundChannelRouter.dueChannelId(
                soundEnabled = true,
                selectedSoundId = SoundAssetIds.builtin(12),
            ),
        )
    }

    @Test
    fun bundledUri_isNameBasedAndroidResource() {
        val asset = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(1))!!
        val uri = SoundUriResolver.forBuiltin(context, asset)
        assertEquals(ContentResolver.SCHEME_ANDROID_RESOURCE, uri.scheme)
        assertEquals(context.packageName, uri.authority)
        assertEquals("raw", uri.pathSegments[0])
        assertEquals("notif_practice_01", uri.pathSegments[1])
        assertFalse(uri.lastPathSegment!!.all { it.isDigit() })
    }

    @Test
    fun ensureChannelsCreated_doesNotCreateAllCustomChannels() {
        val presenter = AndroidPracticeNotificationPresenter(context, notificationManager)
        presenter.ensureChannelsCreated()
        val customCount = notificationManager.notificationChannels.count {
            it.id.startsWith("practice_due_custom_v1_")
        }
        assertEquals(0, customCount)
        assertNotNull(notificationManager.getNotificationChannel(PracticeNotificationChannels.DUE_SOUND))
        assertNotNull(notificationManager.getNotificationChannel(PracticeNotificationChannels.DUE_SILENT))
    }

    @Test
    fun ensureCustom_createsOnlyRequestedBuiltinChannel_withSoundUri() {
        val asset = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(5))!!
        PracticeDueSoundChannelRouter.ensureCustomDueSoundChannel(
            context,
            notificationManager,
            asset,
        )
        val channel = notificationManager.getNotificationChannel("practice_due_custom_v1_05")
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel!!.importance)
        assertNotNull(channel.sound)
        assertEquals("notif_practice_05", channel.sound!!.lastPathSegment)
        assertNull(notificationManager.getNotificationChannel("practice_due_custom_v1_01"))
        assertEquals(
            1,
            notificationManager.notificationChannels.count { it.id.startsWith("practice_due_custom_v1_") },
        )
    }

    @Test
    fun presenter_postsBuiltinToCustomChannel_andQuietCatchUpStaysSilent() {
        val presenter = AndroidPracticeNotificationPresenter(context, notificationManager)
        presenter.showNotification(
            NotificationShowPlan(
                occurrenceId = 1L,
                plannedAtEpochMillis = 1000L,
                questionTextSnapshot = "q",
                soundEnabled = true,
                selectedSoundId = SoundAssetIds.builtin(2),
            ),
        )
        val posted = notificationManager.activeNotifications.single()
        assertEquals("practice_due_custom_v1_02", posted.notification.channelId)
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_02"))

        presenter.showNotification(
            NotificationShowPlan(
                occurrenceId = 1L,
                plannedAtEpochMillis = 1000L,
                questionTextSnapshot = "q",
                soundEnabled = true,
                selectedSoundId = SoundAssetIds.builtin(2),
                suppressAlert = true,
            ),
        )
        val quiet = notificationManager.activeNotifications.single()
        assertEquals(PracticeNotificationChannels.DUE_SILENT, quiet.notification.channelId)
    }
}
