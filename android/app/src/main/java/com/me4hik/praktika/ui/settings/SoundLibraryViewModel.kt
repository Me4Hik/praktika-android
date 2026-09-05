package com.me4hik.praktika.ui.settings

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.R
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.notification.PracticeDueSoundChannelRouter
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.sound.MediaPlayerSoundPreviewPlayer
import com.me4hik.praktika.sound.SoundAssetIds
import com.me4hik.praktika.sound.SoundAssetSource
import com.me4hik.praktika.sound.SoundDisplayNames
import com.me4hik.praktika.sound.SoundPreviewPlayer
import com.me4hik.praktika.sound.SoundPreviewUris
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoundLibraryViewModel(
    application: Application,
    private val soundPreferenceRepository: SoundPreferenceRepository,
    private val previewPlayer: SoundPreviewPlayer,
    private val notificationManager: NotificationManager =
        application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
    private val commandDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        SoundLibraryUiState(
            selectedSoundId = SoundAssetIds.SYSTEM_DEFAULT,
            selectedDisplayName = SoundDisplayNames.forAsset(
                application,
                BuiltinSoundCatalog.SYSTEM_DEFAULT,
            ),
            soundEnabled = true,
            items = emptyList(),
            hiddenCount = 0,
            currentlyPreviewingId = null,
        ),
    )
    val uiState: StateFlow<SoundLibraryUiState> = _uiState.asStateFlow()

    private var currentlyPreviewingId: String? = null

    init {
        previewPlayer.onPlaybackEnded = {
            currentlyPreviewingId = null
            _uiState.update { it.copy(currentlyPreviewingId = null) }
        }
        viewModelScope.launch {
            combine(
                soundPreferenceRepository.selectedSoundId,
                soundPreferenceRepository.hiddenBuiltinIds,
                soundPreferenceRepository.soundEnabled,
            ) { selectedId, hidden, soundEnabled ->
                Triple(selectedId, hidden, soundEnabled)
            }.collect { (selectedId, hidden, soundEnabled) ->
                publish(selectedId, hidden, soundEnabled, currentlyPreviewingId)
            }
        }
    }

    fun select(id: String) {
        viewModelScope.launch {
            try {
                withContext(commandDispatcher) {
                    soundPreferenceRepository.selectSound(id)
                    val resolved = BuiltinSoundCatalog.resolveOrDefault(id)
                    if (resolved.source == SoundAssetSource.BUILTIN) {
                        PracticeDueSoundChannelRouter.ensureCustomDueSoundChannel(
                            context = getApplication(),
                            notificationManager = notificationManager,
                            asset = resolved,
                        )
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "selectSound failed", exception)
                _uiState.update { it.copy(messageResId = R.string.sound_library_error_generic) }
            }
        }
    }

    fun togglePreview(id: String) {
        if (currentlyPreviewingId == id) {
            stopPreview()
            return
        }
        val asset = BuiltinSoundCatalog.resolveOrDefault(id)
        val app = getApplication<Application>()
        val uri = SoundPreviewUris.forAsset(app, asset)
        if (uri == null) {
            _uiState.update { it.copy(messageResId = R.string.sound_library_preview_unavailable) }
            return
        }
        previewPlayer.stop()
        val started = previewPlayer.play(app, uri)
        if (!started) {
            currentlyPreviewingId = null
            _uiState.update {
                it.copy(
                    currentlyPreviewingId = null,
                    messageResId = R.string.sound_library_preview_unavailable,
                )
            }
            return
        }
        currentlyPreviewingId = asset.id
        _uiState.update {
            it.copy(currentlyPreviewingId = asset.id, messageResId = null)
        }
    }

    fun hide(id: String) {
        viewModelScope.launch {
            try {
                if (currentlyPreviewingId == id) {
                    stopPreview()
                }
                withContext(commandDispatcher) {
                    soundPreferenceRepository.hideBuiltin(id)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "hideBuiltin failed", exception)
                _uiState.update { it.copy(messageResId = R.string.sound_library_error_generic) }
            }
        }
    }

    fun restoreAllHidden() {
        viewModelScope.launch {
            try {
                withContext(commandDispatcher) {
                    soundPreferenceRepository.restoreAllHidden()
                }
            } catch (exception: Exception) {
                Log.e(TAG, "restoreAllHidden failed", exception)
                _uiState.update { it.copy(messageResId = R.string.sound_library_error_generic) }
            }
        }
    }

    fun stopPreview() {
        previewPlayer.stop()
        currentlyPreviewingId = null
        _uiState.update { it.copy(currentlyPreviewingId = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(messageResId = null) }
    }

    /** Stops and releases the preview player; also invoked from [onCleared]. */
    fun releasePreviewResources() {
        previewPlayer.onPlaybackEnded = null
        previewPlayer.release()
        currentlyPreviewingId = null
        _uiState.update { it.copy(currentlyPreviewingId = null) }
    }

    override fun onCleared() {
        releasePreviewResources()
        super.onCleared()
    }

    private fun publish(
        selectedId: String,
        hidden: Set<String>,
        soundEnabled: Boolean,
        previewingId: String?,
    ) {
        val context = getApplication<Application>()
        val resolvedSelected = BuiltinSoundCatalog.resolveOrDefault(selectedId)
        val visible = BuiltinSoundCatalog.visible(hidden)
        _uiState.value = SoundLibraryUiState(
            selectedSoundId = resolvedSelected.id,
            selectedDisplayName = SoundDisplayNames.forAsset(context, resolvedSelected),
            soundEnabled = soundEnabled,
            items = visible.map { asset ->
                val previewUri = SoundPreviewUris.forAsset(context, asset)
                SoundLibraryItemUi(
                    asset = asset,
                    displayName = SoundDisplayNames.forAsset(context, asset),
                    selected = asset.id == resolvedSelected.id,
                    canHide = asset.source == SoundAssetSource.BUILTIN,
                    previewAvailable = previewUri != null,
                )
            },
            hiddenCount = hidden.count { BuiltinSoundCatalog.containsBuiltinId(it) },
            currentlyPreviewingId = previewingId,
            messageResId = _uiState.value.messageResId,
        )
    }

    companion object {
        private const val TAG = "SoundLibraryViewModel"
    }
}

class SoundLibraryViewModelFactory(
    private val application: Application,
    private val runtime: PraktikaRuntime,
    private val previewPlayer: SoundPreviewPlayer = MediaPlayerSoundPreviewPlayer(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SoundLibraryViewModel::class.java)) {
            return SoundLibraryViewModel(
                application = application,
                soundPreferenceRepository = runtime.soundPreferenceRepository,
                previewPlayer = previewPlayer,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
