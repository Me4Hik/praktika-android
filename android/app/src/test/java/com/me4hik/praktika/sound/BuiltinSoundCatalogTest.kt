package com.me4hik.praktika.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinSoundCatalogTest {
    @Test
    fun catalog_containsSystemDefaultPlusExactly25Builtins() {
        assertEquals(26, BuiltinSoundCatalog.all().size)
        assertEquals(25, BuiltinSoundCatalog.builtins().size)
        assertEquals(SoundAssetIds.SYSTEM_DEFAULT, BuiltinSoundCatalog.SYSTEM_DEFAULT.id)
    }

    @Test
    fun catalog_omitsGaps_11_16_18() {
        assertNull(BuiltinSoundCatalog.findById(SoundAssetIds.builtin(11)))
        assertNull(BuiltinSoundCatalog.findById(SoundAssetIds.builtin(16)))
        assertNull(BuiltinSoundCatalog.findById(SoundAssetIds.builtin(18)))
        assertFalse(11 in BuiltinSoundCatalog.BUILTIN_NUMBERS)
        assertFalse(16 in BuiltinSoundCatalog.BUILTIN_NUMBERS)
        assertFalse(18 in BuiltinSoundCatalog.BUILTIN_NUMBERS)
    }

    @Test
    fun findById_and_resourceNames_matchStableMapping() {
        val asset = BuiltinSoundCatalog.findById(SoundAssetIds.builtin(1))
        assertNotNull(asset)
        assertEquals("notif_practice_01", asset!!.resourceName)
        assertEquals(1, asset.displayNumber)
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
            SoundAssetIds.builtin(12),
            BuiltinSoundCatalog.resolveOrDefault(SoundAssetIds.builtin(12)).id,
        )
    }

    @Test
    fun visible_filtersHiddenBuiltins_butKeepsSystemDefault() {
        val hidden = setOf(SoundAssetIds.builtin(1), SoundAssetIds.builtin(28))
        val visible = BuiltinSoundCatalog.visible(hidden)
        assertTrue(visible.any { it.id == SoundAssetIds.SYSTEM_DEFAULT })
        assertFalse(visible.any { it.id == SoundAssetIds.builtin(1) })
        assertFalse(visible.any { it.id == SoundAssetIds.builtin(28) })
        assertEquals(23, visible.count { it.isBuiltin })
        assertEquals(24, visible.size)
    }
}
