package com.me4hik.praktika.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
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
    fun channelId_mapsKeptBuiltinToCustomV1() {
        assertEquals(
            "practice_due_custom_v1_03",
            PracticeDueSoundChannelRouter.customDueSoundChannelId(SoundAssetIds.builtin(3)),
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
                selectedSoundId = SoundAssetIds.builtin(3),
            ),
        )
    }

    @Test
    fun dueChannelId_builtinUsesCustomChannel() {
        assertEquals(
            "practice_due_custom_v1_13",
            PracticeDueSoundChannelRouter.dueChannelId(
                soundEnabled = true,
                selectedSoundId = SoundAssetIds.builtin(13),
            ),
        )
    }

    @Test
    fun dueChannelId_removedBuiltinFallsBackToSystemDueSound() {
        assertEquals(
            PracticeNotificationChannels.DUE_SOUND,
            PracticeDueSoundChannelRouter.dueChannelId(
                soundEnabled = true,
                selectedSoundId = SoundAssetIds.builtin(1),
            ),
        )
    }

    @Test
    fun bundledUri_isNameBasedAndroidResource() {
        val asset = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(3))!!
        val uri = SoundUriResolver.forBuiltin(context, asset)
        assertEquals(ContentResolver.SCHEME_ANDROID_RESOURCE, uri.scheme)
        assertEquals(context.packageName, uri.authority)
        assertEquals("raw", uri.pathSegments[0])
        assertEquals("notif_practice_03", uri.pathSegments[1])
        assertFalse(uri.lastPathSegment!!.all { it.isDigit() })
    }

    @Test
    fun ensureChannelsCreated_doesNotCreateAllCustomChannels() {
        val presenter = AndroidPracticeNotificationPresenter(context, notificationManager)
        presenter.ensureChannelsCreated()
        assertEquals(0, customChannelCount())
        assertNotNull(notificationManager.getNotificationChannel(PracticeNotificationChannels.DUE_SOUND))
        assertNotNull(notificationManager.getNotificationChannel(PracticeNotificationChannels.DUE_SILENT))
    }

    @Test
    fun ensureCustom_createsOnlyRequestedBuiltinChannel_withSoundUri() {
        val asset = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(8))!!
        PracticeDueSoundChannelRouter.ensureCustomDueSoundChannel(
            context,
            notificationManager,
            asset,
        )
        val channel = notificationManager.getNotificationChannel("practice_due_custom_v1_08")
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel!!.importance)
        assertNotNull(channel.sound)
        assertEquals("notif_practice_08", channel.sound!!.lastPathSegment)
        assertNull(notificationManager.getNotificationChannel("practice_due_custom_v1_03"))
        assertEquals(1, customChannelCount())
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
                selectedSoundId = SoundAssetIds.builtin(10),
            ),
        )
        val posted = notificationManager.activeNotifications.single()
        assertEquals("practice_due_custom_v1_10", posted.notification.channelId)
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_10"))

        presenter.showNotification(
            NotificationShowPlan(
                occurrenceId = 1L,
                plannedAtEpochMillis = 1000L,
                questionTextSnapshot = "q",
                soundEnabled = true,
                selectedSoundId = SoundAssetIds.builtin(10),
                suppressAlert = true,
            ),
        )
        val quiet = notificationManager.activeNotifications.single()
        assertEquals(PracticeNotificationChannels.DUE_SILENT, quiet.notification.channelId)
    }

    @Test
    fun prune_removesOrphans_keepsSelectedBuiltinChannel() {
        seedCustomChannels(3, 8, 27)
        seedBaseChannels()
        seedForeignChannel()

        PracticeDueSoundChannelRouter.pruneOrphanCustomDueSoundChannels(
            notificationManager,
            SoundAssetIds.builtin(8),
        )

        assertNull(notificationManager.getNotificationChannel("practice_due_custom_v1_03"))
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_08"))
        assertNull(notificationManager.getNotificationChannel("practice_due_custom_v1_27"))
        assertBaseChannelsUntouched()
        assertNotNull(notificationManager.getNotificationChannel("foreign_other_app_channel"))
        assertEquals(1, customChannelCount())
    }

    @Test
    fun prune_systemDefault_clearsAllCustomChannels() {
        seedCustomChannels(3, 28)
        seedBaseChannels()

        PracticeDueSoundChannelRouter.pruneOrphanCustomDueSoundChannels(
            notificationManager,
            SoundAssetIds.SYSTEM_DEFAULT,
        )

        assertEquals(0, customChannelCount())
        assertBaseChannelsUntouched()
    }

    @Test
    fun prune_removedCatalogSoundChannel_isDeletedEvenIfPreviouslySelectedRaw() {
        // Channel for removed builtin_01 may still exist on upgraded installs.
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "practice_due_custom_v1_01",
                "legacy removed",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        seedCustomChannels(27)

        PracticeDueSoundChannelRouter.pruneOrphanCustomDueSoundChannels(
            notificationManager,
            SoundAssetIds.builtin(1), // resolves to system_default
        )

        assertNull(notificationManager.getNotificationChannel("practice_due_custom_v1_01"))
        assertNull(notificationManager.getNotificationChannel("practice_due_custom_v1_27"))
        assertEquals(0, customChannelCount())
    }

    @Test
    fun prune_switchBuiltinAtoB_leavesOnlyB() {
        seedCustomChannels(3, 8)
        PracticeDueSoundChannelRouter.pruneOrphanCustomDueSoundChannels(
            notificationManager,
            SoundAssetIds.builtin(3),
        )
        assertEquals(1, customChannelCount())
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_03"))

        val assetB = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(8))!!
        PracticeDueSoundChannelRouter.ensureCustomDueSoundChannel(
            context,
            notificationManager,
            assetB,
        )
        PracticeDueSoundChannelRouter.pruneOrphanCustomDueSoundChannels(
            notificationManager,
            SoundAssetIds.builtin(8),
        )

        assertEquals(1, customChannelCount())
        assertNull(notificationManager.getNotificationChannel("practice_due_custom_v1_03"))
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_08"))
    }

    @Test
    fun prune_isIdempotent_andDoesNotTouchUnknownChannels() {
        seedCustomChannels(13)
        seedForeignChannel()
        seedBaseChannels()

        repeat(3) {
            PracticeDueSoundChannelRouter.pruneOrphanCustomDueSoundChannels(
                notificationManager,
                SoundAssetIds.builtin(13),
            )
        }

        assertEquals(1, customChannelCount())
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_13"))
        assertNotNull(notificationManager.getNotificationChannel("foreign_other_app_channel"))
        assertBaseChannelsUntouched()
    }

    @Test
    fun prune_additionalKeepPreservesHostingChannelEvenIfNotSelected() {
        seedCustomChannels(3, 8)
        PracticeDueSoundChannelRouter.pruneOrphanCustomDueSoundChannels(
            notificationManager = notificationManager,
            selectedSoundId = SoundAssetIds.builtin(8),
            additionalKeepChannelIds = setOf("practice_due_custom_v1_03"),
        )
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_03"))
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_08"))
        assertEquals(2, customChannelCount())
    }

    @Test
    fun presenterPrune_delegatesToRouter() {
        seedCustomChannels(3, 8)
        val presenter = AndroidPracticeNotificationPresenter(context, notificationManager)
        presenter.pruneCustomDueSoundChannels(SoundAssetIds.builtin(3))
        assertEquals(1, customChannelCount())
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_03"))
    }

    private fun seedCustomChannels(vararg numbers: Int) {
        numbers.forEach { number ->
            val asset = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(number))!!
            PracticeDueSoundChannelRouter.ensureCustomDueSoundChannel(
                context,
                notificationManager,
                asset,
            )
        }
    }

    private fun seedBaseChannels() {
        AndroidPracticeNotificationPresenter(context, notificationManager).ensureChannelsCreated()
    }

    private fun seedForeignChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "foreign_other_app_channel",
                "Foreign",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun assertBaseChannelsUntouched() {
        assertNotNull(notificationManager.getNotificationChannel(PracticeNotificationChannels.SOUND))
        assertNotNull(notificationManager.getNotificationChannel(PracticeNotificationChannels.SILENT))
        assertNotNull(notificationManager.getNotificationChannel(PracticeNotificationChannels.DUE_SOUND))
        assertNotNull(notificationManager.getNotificationChannel(PracticeNotificationChannels.DUE_SILENT))
        assertNotNull(notificationManager.getNotificationChannel(PracticeNotificationChannels.SNOOZED))
    }

    private fun customChannelCount(): Int {
        return notificationManager.notificationChannels.count {
            PracticeDueSoundChannelRouter.isCustomDueSoundChannelId(it.id)
        }
    }
}
