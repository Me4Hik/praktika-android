// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - ViewModel factory архива
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - factory questions/history
package com.me4hik.praktika.ui.archive

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.savedstate.SavedStateRegistryOwner
import com.me4hik.praktika.runtime.PraktikaRuntime

class ArchiveViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val runtime: PraktikaRuntime,
    private val epochDay: Long? = null,
    private val questionId: Int? = null,
) : AbstractSavedStateViewModelFactory(owner, null) {

    private val zoneIdProvider = TimeProviderArchiveZoneIdProvider(runtime.timeProvider)
    private val displayFormatter = ArchiveDisplayFormatter()

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle,
    ): T {
        return when {
            modelClass.isAssignableFrom(ArchiveDatesViewModel::class.java) -> {
                ArchiveDatesViewModel(
                    archiveReadRepository = runtime.archiveReadRepository,
                    zoneIdProvider = zoneIdProvider,
                    displayFormatter = displayFormatter,
                ) as T
            }
            modelClass.isAssignableFrom(ArchiveDayViewModel::class.java) -> {
                ArchiveDayViewModel(
                    archiveReadRepository = runtime.archiveReadRepository,
                    answerDeleteRepository = runtime.answerDeleteRepository,
                    zoneIdProvider = zoneIdProvider,
                    displayFormatter = displayFormatter,
                    epochDay = epochDay,
                ) as T
            }
            modelClass.isAssignableFrom(ArchiveQuestionsViewModel::class.java) -> {
                ArchiveQuestionsViewModel(
                    archiveReadRepository = runtime.archiveReadRepository,
                ) as T
            }
            modelClass.isAssignableFrom(ArchiveQuestionHistoryViewModel::class.java) -> {
                ArchiveQuestionHistoryViewModel(
                    archiveReadRepository = runtime.archiveReadRepository,
                    answerDeleteRepository = runtime.answerDeleteRepository,
                    zoneIdProvider = zoneIdProvider,
                    displayFormatter = displayFormatter,
                    questionId = questionId,
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        fun dates(
            owner: SavedStateRegistryOwner,
            runtime: PraktikaRuntime,
        ): ViewModelProvider.Factory = ArchiveViewModelFactory(owner, runtime)

        fun day(
            owner: SavedStateRegistryOwner,
            runtime: PraktikaRuntime,
            epochDay: Long,
        ): ViewModelProvider.Factory = ArchiveViewModelFactory(owner, runtime, epochDay = epochDay)

        fun questions(
            owner: SavedStateRegistryOwner,
            runtime: PraktikaRuntime,
        ): ViewModelProvider.Factory = ArchiveViewModelFactory(owner, runtime)

        fun questionHistory(
            owner: SavedStateRegistryOwner,
            runtime: PraktikaRuntime,
            questionId: Int,
        ): ViewModelProvider.Factory = ArchiveViewModelFactory(owner, runtime, questionId = questionId)
    }
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
