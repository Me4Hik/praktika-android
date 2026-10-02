package com.me4hik.praktika.actiontoursmoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundSmokeProofTest {
    @Test
    fun titleAbsent_selectedPresent_screenAccepted() {
        assertTrue(
            SoundSmokeProof.soundLibraryScreenAccepted(
                hasProgressTask4 = true,
                tipLooksLikeSoundTask = true,
                hasSelectedCdOrChecked = true,
            ),
        )
    }

    @Test
    fun missingSelected_notAccepted() {
        assertFalse(
            SoundSmokeProof.soundLibraryScreenAccepted(
                hasProgressTask4 = true,
                tipLooksLikeSoundTask = true,
                hasSelectedCdOrChecked = false,
            ),
        )
    }

    @Test
    fun unselectedFallback_forbidden() {
        assertFalse(SoundSmokeProof.allowUnselectedSoundFallback())
    }

    @Test
    fun selectedCdConstant_matchesProduction() {
        assertEquals("Выбрано", SoundSmokeProof.SELECTED_CONTENT_DESCRIPTION)
        assertEquals(ProductionTourTargets.SOUND_SELECTED_CD, SoundSmokeProof.SELECTED_CONTENT_DESCRIPTION)
    }
}
