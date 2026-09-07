package com.me4hik.praktika.ui.tour

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TourTargetRegistry {
    private val _targets = MutableStateFlow<Map<TourTargetId, Rect>>(emptyMap())
    val targets: StateFlow<Map<TourTargetId, Rect>> = _targets.asStateFlow()

    private val _generation = MutableStateFlow(0)
    /** Bumped on [clear] so composed targets can re-publish bounds. */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    fun update(id: TourTargetId, boundsInWindow: Rect) {
        _targets.update { current ->
            if (current[id] == boundsInWindow) current else current + (id to boundsInWindow)
        }
    }

    fun remove(id: TourTargetId) {
        _targets.update { current ->
            if (!current.containsKey(id)) current else current - id
        }
    }

    fun bounds(id: TourTargetId): Rect? = _targets.value[id]

    fun clear() {
        _targets.value = emptyMap()
        _generation.update { it + 1 }
    }
}
