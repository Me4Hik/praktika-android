package com.me4hik.praktika.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinSoundCatalogTest {
    @Test
    fun catalog_containsSystemDefaultPlusExactAllowlistBuiltins() {
        assertEquals(11, BuiltinSoundCatalog.all().size)
        assertEquals(10, BuiltinSoundCatalog.builtins().size)
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, BuiltinSoundCatalog.SYSTEM_DEFAULT.id)
        assertEquals(
            listOf(3, 8, 10, 13, 23, 24, 25, 26, 27, 28),
            BuiltinSoundCatalog.BUILTIN_NUMBERS,
        )
    }

    @Test
    fun catalog_omitsRemovedAndHistoricalGaps() {
        val removed = listOf(1, 2, 4, 5, 6, 7, 9, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21, 22)
        removed.forEach { number ->
            assertNull(BuiltinSoundCatalog.findById(SoundAssetIds.builtin(number)))
            assertFalse(number in BuiltinSoundCatalog.BUILTIN_NUMBERS)
            assertEquals(
                SoundAssetIds.SYSTEM_DEFAULT,
                BuiltinSoundCatalog.resolveOrDefault(SoundAssetIds.builtin(number)).id,
            )
        }
    }

    @Test
    fun findById_and_resourceNames_matchStableMappingForKeptBuiltins() {
        val asset = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(3))
        assertNotNull(asset)
        assertEquals("notif_practice_03", asset!!.resourceName)
        assertEquals(3, asset.displayNumber)
        assertFalse(asset.deletable)

        val twentyEight = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(28))!!
        assertEquals("notif_practice_28", twentyEight.resourceName)
    }

    @Test
    fun resolveOrDefault_unknownFallsBackToSystemDefault() {
        assertEquals(
            SoundAssetIds.SYSTEM_DEFAULT,
            BuiltinSoundCatalog.resolveOrDefault("nope").id,
        )
        assertEquals(
            SoundAssetIds.SYSTEM_DEFAULT,
            BuiltinSoundCatalog.resolveOrDefault(null).id,
        )
        assertEquals(
            SoundAssetIds.SYSTEM_DEFAULT,
            BuiltinSoundCatalog.resolveOrDefault("").id,
        )
        assertEquals(
            SoundAssetIds.builtin(13),
            BuiltinSoundCatalog.resolveOrDefault(SoundAssetIds.builtin(13)).id,
        )
    }

    @Test
    fun visible_filtersHiddenBuiltins_butKeepsSystemDefault() {
        val hidden = setOf(SoundAssetIds.builtin(3), SoundAssetIds.builtin(28))
        val visible = BuiltinSoundCatalog.visible(hidden)
        assertTrue(visible.any { it.id == SoundAssetIds.SYSTEM_DEFAULT })
        assertFalse(visible.any { it.id == SoundAssetIds.builtin(3) })
        assertFalse(visible.any { it.id == SoundAssetIds.builtin(28) })
        assertEquals(8, visible.count { it.isBuiltin })
        assertEquals(9, visible.size)
    }

    @Test
    fun containsBuiltinId_falseForRemovedIds() {
        assertFalse(BuiltinSoundCatalog.containsBuiltinId(SoundAssetIds.builtin(1)))
        assertTrue(BuiltinSoundCatalog.containsBuiltinId(SoundAssetIds.builtin(27)))
    }
}
