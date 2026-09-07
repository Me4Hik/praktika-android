package com.me4hik.praktika.ui.settings

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.notification.PracticeDueSoundChannelRouter
import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.sound.SoundAssetIds
import com.me4hik.praktika.sound.SoundPreviewPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SoundLibraryViewModelTest {
    private lateinit var application: Application
    private lateinit var notificationManager: NotificationManager
    private lateinit var repository: InMemorySoundPreferenceRepository
    private lateinit var previewPlayer: FakeSoundPreviewPlayer
    private lateinit var syncRequester: RecordingSyncRequester
    private lateinit var viewModel: SoundLibraryViewModel
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
        notificationManager =
            application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notificationChannels
            .map { it.id }
            .forEach { notificationManager.deleteNotificationChannel(it) }
        repository = InMemorySoundPreferenceRepository()
        previewPlayer = FakeSoundPreviewPlayer()
        syncRequester = RecordingSyncRequester()
        viewModel = SoundLibraryViewModel(
            application = application,
            soundPreferenceRepository = repository,
            previewPlayer = previewPlayer,
            notificationSyncRequester = syncRequester,
            notificationManager = notificationManager,
            commandDispatcher = dispatcher,
        )
        // runCurrent only: advanceUntilIdle would spin forever once preview ticker is live.
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        viewModel.releasePreviewResources()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun pump() {
        dispatcher.scheduler.runCurrent()
    }

    private fun pumpMs(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun cleanList_isSystemDefaultPlusAllowlistBuiltins() = runBlocking {
        pump()
        val state = viewModel.uiState.value
        assertTrue(state.items.isNotEmpty())
        assertEquals(11, state.items.size)
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, state.items.first().asset.id)
        assertEquals(10, state.items.count { it.asset.isBuiltin })
        assertEquals(0, state.hiddenCount)
        assertTrue(state.items.none { it.asset.id == SoundAssetIds.builtin(1) })
        assertTrue(state.items.any { it.asset.id == SoundAssetIds.builtin(27) })
    }

    @Test
    fun selectedIndicator_followsSelect() = runBlocking {
        viewModel.select(SoundAssetIds.builtin(3))
        pump()
        val state = viewModel.uiState.value
        assertEquals(SoundAssetIds.builtin(3), state.selectedSoundId)
        assertTrue(state.items.first { it.asset.id == SoundAssetIds.builtin(3) }.selected)
        assertFalse(state.items.first { it.asset.id == SoundAssetIds.SYSTEM_DEFAULT }.selected)
    }

    @Test
    fun select_doesNotStartPreview() = runBlocking {
        viewModel.select(SoundAssetIds.builtin(8))
        pump()
        assertTrue(previewPlayer.playCalls.isEmpty())
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(0L, viewModel.uiState.value.previewPositionMs)
        assertNull(viewModel.uiState.value.previewDurationMs)
    }

    @Test
    fun select_requestsSoundChangedSync() = runBlocking {
        viewModel.select(SoundAssetIds.builtin(10))
        pump()
        assertEquals(listOf(NotificationSyncReason.SOUND_CHANGED), syncRequester.reasons)
    }

    @Test
    fun preview_oneAtATime_andRetapStops() = runBlocking {
        viewModel.togglePreview(SoundAssetIds.builtin(3))
        pump()
        assertEquals(SoundAssetIds.builtin(3), viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(1, previewPlayer.playCalls.size)

        viewModel.togglePreview(SoundAssetIds.builtin(8))
        pump()
        assertEquals(SoundAssetIds.builtin(8), viewModel.uiState.value.currentlyPreviewingId)
        assertTrue(previewPlayer.stopCount >= 1)
        assertEquals(2, previewPlayer.playCalls.size)

        viewModel.togglePreview(SoundAssetIds.builtin(8))
        pump()
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(0L, viewModel.uiState.value.previewPositionMs)
        assertNull(viewModel.uiState.value.previewDurationMs)
    }

    @Test
    fun preview_play_setsIdDurationAndPosition() = runBlocking {
        previewPlayer.configuredDurationMs = 28_000L
        previewPlayer.configuredPositionMs = 0L
        viewModel.togglePreview(SoundAssetIds.builtin(3))
        pump()
        val state = viewModel.uiState.value
        assertEquals(SoundAssetIds.builtin(3), state.currentlyPreviewingId)
        assertEquals(28_000L, state.previewDurationMs)
        assertEquals(0L, state.previewPositionMs)
    }

    @Test
    fun preview_ticker_advancesPosition() = runBlocking {
        previewPlayer.configuredDurationMs = 10_000L
        previewPlayer.configuredPositionMs = 0L
        viewModel.togglePreview(SoundAssetIds.builtin(3))
        pump()
        previewPlayer.configuredPositionMs = 450L
        pumpMs(SoundLibraryViewModel.PREVIEW_TICK_MS)
        assertEquals(450L, viewModel.uiState.value.previewPositionMs)
        assertEquals(SoundAssetIds.builtin(3), viewModel.uiState.value.currentlyPreviewingId)
    }

    @Test
    fun naturalPlaybackEnd_clearsPreviewingIconState() = runBlocking {
        viewModel.togglePreview(SoundAssetIds.builtin(3))
        pump()
        assertEquals(SoundAssetIds.builtin(3), viewModel.uiState.value.currentlyPreviewingId)
        previewPlayer.finishNaturally()
        pump()
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(0L, viewModel.uiState.value.previewPositionMs)
        assertNull(viewModel.uiState.value.previewDurationMs)
    }

    @Test
    fun naturalPlaybackEnd_stopsTicker_noFurtherUpdates() = runBlocking {
        previewPlayer.configuredDurationMs = 10_000L
        viewModel.togglePreview(SoundAssetIds.builtin(3))
        pump()
        previewPlayer.finishNaturally()
        pump()
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
        previewPlayer.configuredPositionMs = 9_999L
        pumpMs(SoundLibraryViewModel.PREVIEW_TICK_MS * 3)
        assertEquals(0L, viewModel.uiState.value.previewPositionMs)
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
    }

    @Test
    fun manualStop_clearsAndIgnoresFurtherTicks() = runBlocking {
        previewPlayer.configuredDurationMs = 10_000L
        viewModel.togglePreview(SoundAssetIds.builtin(8))
        pump()
        viewModel.stopPreview()
        pump()
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
        previewPlayer.configuredPositionMs = 800L
        pumpMs(SoundLibraryViewModel.PREVIEW_TICK_MS * 2)
        assertEquals(0L, viewModel.uiState.value.previewPositionMs)
        assertNull(viewModel.uiState.value.previewDurationMs)
    }

    @Test
    fun preview_switchAtoB_ignoresStaleAUpdates() = runBlocking {
        previewPlayer.configuredDurationMs = 30_000L
        previewPlayer.configuredPositionMs = 100L
        viewModel.togglePreview(SoundAssetIds.builtin(3))
        pump()
        assertEquals(SoundAssetIds.builtin(3), viewModel.uiState.value.currentlyPreviewingId)

        previewPlayer.configuredDurationMs = 12_000L
        previewPlayer.configuredPositionMs = 0L
        viewModel.togglePreview(SoundAssetIds.builtin(8))
        pump()
        assertEquals(SoundAssetIds.builtin(8), viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(12_000L, viewModel.uiState.value.previewDurationMs)

        previewPlayer.configuredPositionMs = 700L
        pumpMs(SoundLibraryViewModel.PREVIEW_TICK_MS)
        assertEquals(SoundAssetIds.builtin(8), viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(700L, viewModel.uiState.value.previewPositionMs)
        assertEquals(12_000L, viewModel.uiState.value.previewDurationMs)
    }

    @Test
    fun hideActivePreview_stopsAndClears() = runBlocking {
        viewModel.togglePreview(SoundAssetIds.builtin(3))
        pump()
        assertEquals(SoundAssetIds.builtin(3), viewModel.uiState.value.currentlyPreviewingId)
        viewModel.hide(SoundAssetIds.builtin(3))
        pump()
        assertEquals(1, viewModel.uiState.value.hiddenCount)
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(0L, viewModel.uiState.value.previewPositionMs)
        assertNull(viewModel.uiState.value.previewDurationMs)
        assertTrue(previewPlayer.stopCount >= 1)
    }

    @Test
    fun preview_unknownDuration_safeState() = runBlocking {
        previewPlayer.configuredDurationMs = null
        previewPlayer.configuredPositionMs = 120L
        viewModel.togglePreview(SoundAssetIds.builtin(10))
        pump()
        val state = viewModel.uiState.value
        assertEquals(SoundAssetIds.builtin(10), state.currentlyPreviewingId)
        assertNull(state.previewDurationMs)
        assertEquals(120L, state.previewPositionMs)
    }

    @Test
    fun onCleared_releasesPlayer() = runBlocking {
        viewModel.togglePreview(SoundAssetIds.builtin(3))
        pump()
        viewModel.releasePreviewResources()
        pump()
        assertTrue(previewPlayer.releaseCount >= 1)
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(0L, viewModel.uiState.value.previewPositionMs)
        assertNull(viewModel.uiState.value.previewDurationMs)
    }

    @Test
    fun release_stopsTicker_noFurtherUpdates() = runBlocking {
        previewPlayer.configuredDurationMs = 5_000L
        viewModel.togglePreview(SoundAssetIds.builtin(3))
        pump()
        viewModel.releasePreviewResources()
        pump()
        previewPlayer.configuredPositionMs = 4_000L
        pumpMs(SoundLibraryViewModel.PREVIEW_TICK_MS * 2)
        assertEquals(0L, viewModel.uiState.value.previewPositionMs)
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
    }

    @Test
    fun formatPreviewTimeMs_isMinutesColonSeconds() {
        assertEquals("0:00", formatPreviewTimeMs(0))
        assertEquals("0:07", formatPreviewTimeMs(7_000))
        assertEquals("1:05", formatPreviewTimeMs(65_000))
    }

    @Test
    fun hideBuiltin_removesFromList_andHideSelectedFallsBack() = runBlocking {
        viewModel.select(SoundAssetIds.builtin(13))
        pump()
        viewModel.hide(SoundAssetIds.builtin(13))
        pump()
        val state = viewModel.uiState.value
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, state.selectedSoundId)
        assertEquals(1, state.hiddenCount)
        assertTrue(state.items.none { it.asset.id == SoundAssetIds.builtin(13) })
        assertEquals(10, state.items.size)
    }

    @Test
    fun restoreAllHidden_restoresItems() = runBlocking {
        viewModel.hide(SoundAssetIds.builtin(3))
        viewModel.hide(SoundAssetIds.builtin(8))
        pump()
        viewModel.restoreAllHidden()
        pump()
        val state = viewModel.uiState.value
        assertEquals(0, state.hiddenCount)
        assertEquals(11, state.items.size)
    }

    @Test
    fun systemDefault_cannotHide() = runBlocking {
        pump()
        val systemItem = viewModel.uiState.value.items.first {
            it.asset.id == SoundAssetIds.SYSTEM_DEFAULT
        }
        assertFalse(systemItem.canHide)
        viewModel.hide(SoundAssetIds.SYSTEM_DEFAULT)
        pump()
        assertEquals(0, viewModel.uiState.value.hiddenCount)
    }

    @Test
    fun soundDisabled_stillAllowsPreviewAndSelect() = runBlocking {
        repository.setSoundEnabled(false)
        pump()
        viewModel.select(SoundAssetIds.builtin(23))
        pump()
        viewModel.togglePreview(SoundAssetIds.builtin(23))
        pump()
        assertEquals(SoundAssetIds.builtin(23), viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(1, previewPlayer.playCalls.size)
    }

    @Test
    fun openLibrary_doesNotCreateCustomChannels() = runBlocking {
        pump()
        assertTrue(viewModel.uiState.value.items.isNotEmpty())
        assertEquals(0, customChannelCount())
    }

    @Test
    fun preview_doesNotCreateChannel() = runBlocking {
        viewModel.togglePreview(SoundAssetIds.builtin(24))
        pump()
        assertEquals(0, customChannelCount())
    }

    @Test
    fun selectBuiltin_ensuresChannel_butDoesNotPruneBeforeSync() = runBlocking {
        val assetA = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(3))!!
        val assetB = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(8))!!
        PracticeDueSoundChannelRouter.ensureCustomDueSoundChannel(
            application,
            notificationManager,
            assetA,
        )
        PracticeDueSoundChannelRouter.ensureCustomDueSoundChannel(
            application,
            notificationManager,
            assetB,
        )
        assertEquals(2, customChannelCount())

        viewModel.select(SoundAssetIds.builtin(27))
        pump()
        assertEquals(3, customChannelCount())
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_27"))
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_03"))
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_08"))
        assertEquals(listOf(NotificationSyncReason.SOUND_CHANGED), syncRequester.reasons)
    }

    @Test
    fun selectSystemDefault_doesNotPruneBeforeSync() = runBlocking {
        viewModel.select(SoundAssetIds.builtin(10))
        pump()
        assertEquals(1, customChannelCount())
        syncRequester.reasons.clear()
        viewModel.select(SoundAssetIds.SYSTEM_DEFAULT)
        pump()
        assertEquals(1, customChannelCount())
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_10"))
        assertEquals(listOf(NotificationSyncReason.SOUND_CHANGED), syncRequester.reasons)
    }

    private fun customChannelCount(): Int {
        return notificationManager.notificationChannels.count {
            it.id.startsWith("practice_due_custom_v1_")
        }
    }

    private class RecordingSyncRequester : NotificationSyncRequester {
        val reasons = mutableListOf<NotificationSyncReason>()
        override suspend fun requestSync(reason: NotificationSyncReason) {
            reasons += reason
        }
    }

    private class FakeSoundPreviewPlayer : SoundPreviewPlayer {
        val playCalls = mutableListOf<Uri>()
        var stopCount = 0
        var releaseCount = 0
        var configuredDurationMs: Long? = 28_000L
        var configuredPositionMs: Long = 0L
        private var playing = false
        override var onPlaybackEnded: (() -> Unit)? = null

        override val isPlaying: Boolean get() = playing

        override fun play(context: Context, uri: Uri): Boolean {
            playCalls += uri
            playing = true
            return true
        }

        override fun stop() {
            stopCount += 1
            playing = false
        }

        override fun release() {
            releaseCount += 1
            playing = false
        }

        override fun positionMs(): Long = if (playing) configuredPositionMs.coerceAtLeast(0L) else 0L

        override fun durationMs(): Long? = if (playing) configuredDurationMs else null

        fun finishNaturally() {
            playing = false
            onPlaybackEnded?.invoke()
        }
    }

    private class InMemorySoundPreferenceRepository : SoundPreferenceRepository {
        private val enabled = MutableStateFlow(true)
        private val selected = MutableStateFlow(SoundAssetIds.SYSTEM_DEFAULT)
        private val hidden = MutableStateFlow(emptySet<String>())

        override val soundEnabled: Flow<Boolean> = enabled
        override val selectedSoundId: Flow<String> = selected
        override val hiddenBuiltinIds: Flow<Set<String>> = hidden

        override suspend fun setSoundEnabled(enabledValue: Boolean) {
            enabled.value = enabledValue
        }

        override suspend fun selectSound(id: String) {
            val resolved = BuiltinSoundCatalog.resolveOrDefault(id)
            if (resolved.isBuiltin && resolved.id in hidden.value) {
                hidden.value = hidden.value - resolved.id
            }
            selected.value = resolved.id
        }

        override suspend fun hideBuiltin(id: String) {
            val asset = BuiltinSoundCatalog.findById(id) ?: return
            if (!asset.isBuiltin) return
            if (selected.value == asset.id) {
                selected.value = SoundAssetIds.SYSTEM_DEFAULT
            }
            hidden.value = hidden.value + asset.id
        }

        override suspend fun restoreBuiltin(id: String) {
            if (!BuiltinSoundCatalog.containsBuiltinId(id)) return
            hidden.value = hidden.value - id
        }

        override suspend fun restoreAllHidden() {
            hidden.value = emptySet()
        }
    }
}
