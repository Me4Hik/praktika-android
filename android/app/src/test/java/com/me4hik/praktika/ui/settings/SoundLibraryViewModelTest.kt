package com.me4hik.praktika.ui.settings

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.notification.PracticeDueSoundChannelRouter
import com.me4hik.praktika.notification.PracticeNotificationChannels
import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.sound.SoundAssetIds
import com.me4hik.praktika.sound.SoundPreviewPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private lateinit var viewModel: SoundLibraryViewModel
    private val dispatcher = UnconfinedTestDispatcher()

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
        viewModel = SoundLibraryViewModel(
            application = application,
            soundPreferenceRepository = repository,
            previewPlayer = previewPlayer,
            notificationManager = notificationManager,
            commandDispatcher = dispatcher,
        )
    }

    @After
    fun tearDown() {
        viewModel.releasePreviewResources()
        Dispatchers.resetMain()
    }

    @Test
    fun cleanList_isSystemDefaultPlus25Builtins() = runBlocking {
        val state = viewModel.uiState.first { it.items.isNotEmpty() }
        assertEquals(26, state.items.size)
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, state.items.first().asset.id)
        assertEquals(25, state.items.count { it.asset.isBuiltin })
        assertEquals(0, state.hiddenCount)
        assertTrue(state.items.none { it.asset.id == SoundAssetIds.builtin(11) })
    }

    @Test
    fun selectedIndicator_followsSelect() = runBlocking {
        viewModel.select(SoundAssetIds.builtin(3))
        val state = viewModel.uiState.first { it.selectedSoundId == SoundAssetIds.builtin(3) }
        assertTrue(state.items.first { it.asset.id == SoundAssetIds.builtin(3) }.selected)
        assertFalse(state.items.first { it.asset.id == SoundAssetIds.SYSTEM_DEFAULT }.selected)
    }

    @Test
    fun select_doesNotStartPreview() = runBlocking {
        viewModel.select(SoundAssetIds.builtin(2))
        viewModel.uiState.first { it.selectedSoundId == SoundAssetIds.builtin(2) }
        assertTrue(previewPlayer.playCalls.isEmpty())
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
    }

    @Test
    fun preview_oneAtATime_andRetapStops() = runBlocking {
        viewModel.togglePreview(SoundAssetIds.builtin(1))
        assertEquals(SoundAssetIds.builtin(1), viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(1, previewPlayer.playCalls.size)

        viewModel.togglePreview(SoundAssetIds.builtin(2))
        assertEquals(SoundAssetIds.builtin(2), viewModel.uiState.value.currentlyPreviewingId)
        assertTrue(previewPlayer.stopCount >= 1)
        assertEquals(2, previewPlayer.playCalls.size)

        viewModel.togglePreview(SoundAssetIds.builtin(2))
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
    }

    @Test
    fun naturalPlaybackEnd_clearsPreviewingIconState() = runBlocking {
        viewModel.togglePreview(SoundAssetIds.builtin(1))
        assertEquals(SoundAssetIds.builtin(1), viewModel.uiState.value.currentlyPreviewingId)
        previewPlayer.finishNaturally()
        assertNull(viewModel.uiState.value.currentlyPreviewingId)
    }

    @Test
    fun onCleared_releasesPlayer() = runBlocking {
        viewModel.togglePreview(SoundAssetIds.builtin(1))
        viewModel.releasePreviewResources()
        assertTrue(previewPlayer.releaseCount >= 1)
    }

    @Test
    fun hideBuiltin_removesFromList_andHideSelectedFallsBack() = runBlocking {
        viewModel.select(SoundAssetIds.builtin(5))
        viewModel.uiState.first { it.selectedSoundId == SoundAssetIds.builtin(5) }
        viewModel.hide(SoundAssetIds.builtin(5))
        val state = viewModel.uiState.first {
            it.selectedSoundId == SoundAssetIds.SYSTEM_DEFAULT && it.hiddenCount == 1
        }
        assertTrue(state.items.none { it.asset.id == SoundAssetIds.builtin(5) })
        assertEquals(25, state.items.size) // system + 24 builtins
    }

    @Test
    fun restoreAllHidden_restoresItems() = runBlocking {
        viewModel.hide(SoundAssetIds.builtin(1))
        viewModel.hide(SoundAssetIds.builtin(2))
        viewModel.uiState.first { it.hiddenCount == 2 }
        viewModel.restoreAllHidden()
        val state = viewModel.uiState.first { it.hiddenCount == 0 }
        assertEquals(26, state.items.size)
    }

    @Test
    fun systemDefault_cannotHide() = runBlocking {
        val systemItem = viewModel.uiState.first { it.items.isNotEmpty() }
            .items.first { it.asset.id == SoundAssetIds.SYSTEM_DEFAULT }
        assertFalse(systemItem.canHide)
        viewModel.hide(SoundAssetIds.SYSTEM_DEFAULT)
        assertEquals(0, viewModel.uiState.value.hiddenCount)
    }

    @Test
    fun soundDisabled_stillAllowsPreviewAndSelect() = runBlocking {
        repository.setSoundEnabled(false)
        viewModel.uiState.first { !it.soundEnabled }
        viewModel.select(SoundAssetIds.builtin(4))
        viewModel.uiState.first { it.selectedSoundId == SoundAssetIds.builtin(4) }
        viewModel.togglePreview(SoundAssetIds.builtin(4))
        assertEquals(SoundAssetIds.builtin(4), viewModel.uiState.value.currentlyPreviewingId)
        assertEquals(1, previewPlayer.playCalls.size)
    }

    @Test
    fun openLibrary_doesNotCreateCustomChannels() = runBlocking {
        viewModel.uiState.first { it.items.isNotEmpty() }
        val customCount = notificationManager.notificationChannels.count {
            it.id.startsWith("practice_due_custom_v1_")
        }
        assertEquals(0, customCount)
    }

    @Test
    fun preview_doesNotCreateChannel() = runBlocking {
        viewModel.togglePreview(SoundAssetIds.builtin(7))
        assertEquals(0, customChannelCount())
    }

    @Test
    fun selectBuiltin_createsOnlyThatCustomChannel() = runBlocking {
        viewModel.select(SoundAssetIds.builtin(9))
        viewModel.uiState.first { it.selectedSoundId == SoundAssetIds.builtin(9) }
        assertEquals(1, customChannelCount())
        assertNotNull(notificationManager.getNotificationChannel("practice_due_custom_v1_09"))
        assertNull(notificationManager.getNotificationChannel("practice_due_custom_v1_01"))
        assertEquals(
            PracticeNotificationChannels.DUE_SOUND,
            PracticeDueSoundChannelRouter.dueChannelId(true, SoundAssetIds.SYSTEM_DEFAULT),
        )
    }

    private fun customChannelCount(): Int {
        return notificationManager.notificationChannels.count {
            it.id.startsWith("practice_due_custom_v1_")
        }
    }

    private class FakeSoundPreviewPlayer : SoundPreviewPlayer {
        val playCalls = mutableListOf<Uri>()
        var stopCount = 0
        var releaseCount = 0
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
