package com.me4hik.praktika.ui.settings

import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.sound.SoundAssetIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Training assigns play/item targets to the first visible builtin after hidden filtering —
 * never [SoundAssetIds.SYSTEM_DEFAULT], never a hardcoded builtin id.
 */
class SoundLibraryTourTargetingTest {

    @Test
    fun firstTrainingBuiltin_skipsSystemDefault() {
        val items = listOf(
            item(BuiltinSoundCatalog.SYSTEM_DEFAULT, selected = true),
            item(BuiltinSoundCatalog.BUILTINS.first(), selected = false),
        )
        val index = firstVisibleBuiltinIndex(items)
        assertEquals(1, index)
        assertTrue(items[index].asset.isBuiltin)
        assertFalse(items[index].asset.isSystemDefault)
        assertFalse(items[index].asset.id == SoundAssetIds.SYSTEM_DEFAULT)
    }

    @Test
    fun firstTrainingBuiltin_usesFirstAfterHiddenFiltering() {
        val builtins = BuiltinSoundCatalog.BUILTINS
        require(builtins.size >= 2)
        val visible = listOf(
            item(BuiltinSoundCatalog.SYSTEM_DEFAULT, selected = false),
            item(builtins[1], selected = true),
            item(builtins[2], selected = false),
        )
        val index = firstVisibleBuiltinIndex(visible)
        assertEquals(1, index)
        assertEquals(builtins[1].id, visible[index].asset.id)
    }

    @Test
    fun firstTrainingBuiltin_onlyOneIndexReceivesTargets() {
        val items = BuiltinSoundCatalog.all().map { item(it, selected = false) }
        val index = firstVisibleBuiltinIndex(items)
        assertTrue(index >= 0)
        assertEquals(1, items.indices.count { it == index })
        assertTrue(items[index].asset.isBuiltin)
    }

    @Test
    fun previewAndSelect_shareSameBuiltinIndex() {
        val items = listOf(
            item(BuiltinSoundCatalog.SYSTEM_DEFAULT, selected = false),
            item(BuiltinSoundCatalog.BUILTINS.first(), selected = false),
        )
        val index = firstVisibleBuiltinIndex(items)
        assertTrue(items[index].asset.isBuiltin)
        assertEquals(index, firstVisibleBuiltinIndex(items))
    }

    @Test
    fun allHidden_usesRestoreTourTarget_notBuiltinRow() {
        val items = listOf(item(BuiltinSoundCatalog.SYSTEM_DEFAULT, selected = true))
        assertEquals(-1, firstVisibleBuiltinIndex(items))
        assertTrue(useRestoreHiddenTourTarget(items, hiddenCount = 3))
        assertFalse(useRestoreHiddenTourTarget(items, hiddenCount = 0))
    }

    @Test
    fun unreadyEmptyItems_areNotTreatedAsRestoreTargetAlone() {
        // Readiness is explicit on UiState; empty+hiddenCount0 is the VM placeholder.
        assertFalse(useRestoreHiddenTourTarget(emptyList(), hiddenCount = 0))
        assertTrue(useRestoreHiddenTourTarget(emptyList(), hiddenCount = 3))
    }

    @Test
    fun visibleBuiltin_doesNotUseRestoreTourTarget() {
        val items = listOf(
            item(BuiltinSoundCatalog.SYSTEM_DEFAULT, selected = false),
            item(BuiltinSoundCatalog.BUILTINS.first(), selected = true),
        )
        assertFalse(useRestoreHiddenTourTarget(items, hiddenCount = 2))
        assertFalse(useRestoreHiddenTourTarget(items, hiddenCount = 0))
    }

    private fun item(asset: com.me4hik.praktika.sound.SoundAsset, selected: Boolean) =
        SoundLibraryItemUi(
            asset = asset,
            displayName = asset.id,
            selected = selected,
            canHide = asset.isBuiltin,
            previewAvailable = true,
        )
}
