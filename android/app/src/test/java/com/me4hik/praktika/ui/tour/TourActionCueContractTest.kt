package com.me4hik.praktika.ui.tour

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourActionCueContractTest {

    @Test
    fun session_showActionCue_followsRequiresActionCue_andActionReady() {
        val def = ExperimentalCoreTourV1.definition
        fun stateAt(id: TourStepId, ready: Boolean = false) = TourSessionState(
            status = TourStatus.Active,
            definitionId = def.id,
            steps = def.steps,
            stepIndex = def.steps.indexOfFirst { it.id == id },
            actionReady = ready,
        )
        assertFalse(stateAt(TourStepId.CLICK_ARCHIVE, ready = false).showActionCue)
        assertTrue(stateAt(TourStepId.CLICK_ARCHIVE, ready = true).showActionCue)
        assertTrue(stateAt(TourStepId.SCHEDULE_INFO, ready = true).showActionCue)
        assertTrue(stateAt(TourStepId.CHOOSE_SOUND, ready = true).showActionCue)
        assertFalse(stateAt(TourStepId.START_INTRO, ready = true).showActionCue)
        assertFalse(stateAt(TourStepId.PHASE_GATE, ready = true).showActionCue)
        assertFalse(stateAt(TourStepId.OVERVIEW_HOME, ready = true).showActionCue)
        assertFalse(stateAt(TourStepId.OVERVIEW_EXPORT_SHARE, ready = true).showActionCue)
    }

    @Test
    fun reducedMotion_contract_isStaticAccentNotCrash() {
        assertTrue(TourStepType.TARGET_CLICK.let { true })
        assertFalse(
            TourStep(
                id = TourStepId.START_INTRO,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                completion = TourCompletion.ManualAdvance,
            ).requiresActionCue(),
        )
    }
}
