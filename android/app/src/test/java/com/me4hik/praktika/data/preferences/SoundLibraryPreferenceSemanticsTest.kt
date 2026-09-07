package com.me4hik.praktika.data.preferences

import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.sound.SoundAssetIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Host contract tests for sound-library preference semantics.
 * Mirrors [DataStoreSoundPreferenceRepository] rules without Android DataStore I/O.
 */
class SoundLibraryPreferenceSemanticsTest {
    private lateinit var repository: InMemorySoundPreferenceRepository

    @Before
    fun setUp() {
        repository = InMemorySoundPreferenceRepository()
    }

    @Test
    fun defaultSelected_isSystemDefault() = runBlocking {
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, repository.selectedSoundId.first())
        assertTrue(repository.hiddenBuiltinIds.first().isEmpty())
    }

    @Test
    fun unknownSelected_resolvesToSystemDefault() = runBlocking {
        repository.selectSound("garbage")
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, repository.selectedSoundId.first())
    }

    @Test
    fun hideBuiltin_addsToHiddenSet() = runBlocking {
        repository.hideBuiltin(SoundAssetIds.builtin(8))
        assertTrue(SoundAssetIds.builtin(8) in repository.hiddenBuiltinIds.first())
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, repository.selectedSoundId.first())
    }

    @Test
    fun hideCurrentlySelected_fallsBackToSystemDefault() = runBlocking {
        repository.selectSound(SoundAssetIds.builtin(10))
        assertEquals(SoundAssetIds.builtin(10), repository.selectedSoundId.first())
        repository.hideBuiltin(SoundAssetIds.builtin(10))
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, repository.selectedSoundId.first())
        assertTrue(SoundAssetIds.builtin(10) in repository.hiddenBuiltinIds.first())
    }

    @Test
    fun restoreHidden_and_restoreAll() = runBlocking {
        repository.hideBuiltin(SoundAssetIds.builtin(3))
        repository.hideBuiltin(SoundAssetIds.builtin(8))
        repository.restoreBuiltin(SoundAssetIds.builtin(3))
        assertFalse(SoundAssetIds.builtin(3) in repository.hiddenBuiltinIds.first())
        assertTrue(SoundAssetIds.builtin(8) in repository.hiddenBuiltinIds.first())
        repository.restoreAllHidden()
        assertTrue(repository.hiddenBuiltinIds.first().isEmpty())
    }

    @Test
    fun systemDefault_cannotBeHidden() = runBlocking {
        repository.hideBuiltin(SoundAssetIds.SYSTEM_DEFAULT)
        assertTrue(repository.hiddenBuiltinIds.first().isEmpty())
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, repository.selectedSoundId.first())
    }

    @Test
    fun selectHiddenBuiltin_restoresIt() = runBlocking {
        repository.hideBuiltin(SoundAssetIds.builtin(3))
        repository.selectSound(SoundAssetIds.builtin(3))
        assertEquals(SoundAssetIds.builtin(3), repository.selectedSoundId.first())
        assertFalse(SoundAssetIds.builtin(3) in repository.hiddenBuiltinIds.first())
    }

    @Test
    fun staleSelectedRemovedId_normalizesPersistedAndEffectiveToSystemDefault() = runBlocking {
        repository.injectRawSelected(SoundAssetIds.builtin(1))
        repository.normalizePersisted()
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, repository.rawSelected())
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, repository.selectedSoundId.first())
    }

    @Test
    fun staleHiddenRemovedIds_areSanitized() = runBlocking {
        repository.injectRawHidden(
            setOf(
                SoundAssetIds.builtin(1),
                SoundAssetIds.builtin(3),
                SoundAssetIds.builtin(12),
            ),
        )
        repository.normalizePersisted()
        assertEquals(setOf(SoundAssetIds.builtin(3)), repository.hiddenBuiltinIds.first())
        assertEquals(setOf(SoundAssetIds.builtin(3)), repository.rawHidden())
    }

    @Test
    fun validSelectedBuiltin_isPreserved() = runBlocking {
        repository.selectSound(SoundAssetIds.builtin(27))
        assertEquals(SoundAssetIds.builtin(27), repository.selectedSoundId.first())
        repository.normalizePersisted()
        assertEquals(SoundAssetIds.builtin(27), repository.selectedSoundId.first())
    }

    private class InMemorySoundPreferenceRepository : SoundPreferenceRepository {
        private val enabled = MutableStateFlow(true)
        private val selected = MutableStateFlow(SoundAssetIds.SYSTEM_DEFAULT)
        private val hidden = MutableStateFlow(emptySet<String>())

        override val soundEnabled: Flow<Boolean> = enabled
        override val selectedSoundId: Flow<String> = selected.map {
            BuiltinSoundCatalog.resolveOrDefault(it).id
        }
        override val hiddenBuiltinIds: Flow<Set<String>> = hidden.map { raw ->
            raw.filterTo(linkedSetOf()) { BuiltinSoundCatalog.containsBuiltinId(it) }
        }

        override suspend fun setSoundEnabled(enabledValue: Boolean) {
            enabled.value = enabledValue
        }

        override suspend fun selectSound(id: String) {
            normalizePersisted()
            val resolved = BuiltinSoundCatalog.resolveOrDefault(id)
            if (resolved.isBuiltin && resolved.id in hidden.value) {
                hidden.value = hidden.value - resolved.id
            }
            selected.value = resolved.id
        }

        override suspend fun hideBuiltin(id: String) {
            normalizePersisted()
            val asset = BuiltinSoundCatalog.findById(id) ?: return
            if (!asset.isBuiltin) return
            if (BuiltinSoundCatalog.resolveOrDefault(selected.value).id == asset.id) {
                selected.value = SoundAssetIds.SYSTEM_DEFAULT
            }
            hidden.value = sanitize(hidden.value) + asset.id
        }

        override suspend fun restoreBuiltin(id: String) {
            if (!BuiltinSoundCatalog.containsBuiltinId(id)) return
            normalizePersisted()
            hidden.value = sanitize(hidden.value) - id
        }

        override suspend fun restoreAllHidden() {
            normalizePersisted()
            hidden.value = emptySet()
        }

        fun injectRawSelected(id: String) {
            selected.value = id
        }

        fun injectRawHidden(ids: Set<String>) {
            hidden.value = ids
        }

        fun rawSelected(): String = selected.value

        fun rawHidden(): Set<String> = hidden.value

        fun normalizePersisted() {
            selected.value = BuiltinSoundCatalog.resolveOrDefault(selected.value).id
            hidden.value = sanitize(hidden.value)
        }

        private fun sanitize(raw: Set<String>): Set<String> =
            raw.filterTo(linkedSetOf()) { BuiltinSoundCatalog.containsBuiltinId(it) }
    }
}
