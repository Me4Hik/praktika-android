package com.me4hik.praktika.ui.tour

import com.me4hik.praktika.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalCoreTourV1Test {

    private val definition = ExperimentalCoreTourV1.definition

    @Test
    fun coreDefinition_hasExactly27Steps() {
        assertEquals(27, definition.steps.size)
        assertEquals("experimental_core_v1", definition.id)
    }

    @Test
    fun coreDefinition_stepOrderAndTypes() {
        val expected = listOf(
            TourStepId.START_INTRO to TourStepType.SHOW_ONLY,
            TourStepId.GO_HOME to TourStepType.SHOW_ONLY,
            TourStepId.HOME_STATUS to TourStepType.SHOW_ONLY,
            TourStepId.CLICK_ARCHIVE to TourStepType.TARGET_CLICK,
            TourStepId.ARCHIVE_HUB_INTRO to TourStepType.SHOW_ONLY,
            TourStepId.CLICK_ARCHIVE_DAYS to TourStepType.TARGET_CLICK,
            TourStepId.ARCHIVE_DAYS_CONTENT to TourStepType.SHOW_ONLY,
            TourStepId.BACK_FROM_DAYS to TourStepType.NAV_BACK,
            TourStepId.CLICK_EXPORT_ACCORDION to TourStepType.TARGET_CLICK,
            TourStepId.EXPORT_INFO to TourStepType.SHOW_ONLY,
            TourStepId.SHARE_INFO to TourStepType.SHOW_ONLY,
            TourStepId.BACK_FROM_ARCHIVE to TourStepType.NAV_BACK,
            TourStepId.CLICK_SETTINGS to TourStepType.TARGET_CLICK,
            TourStepId.SCHEDULE_INFO to TourStepType.SHOW_ONLY,
            TourStepId.CHOOSE_WORDING to TourStepType.USER_CHOICE,
            TourStepId.PAUSE_INFO to TourStepType.SHOW_ONLY,
            TourStepId.BACKUP_INFO to TourStepType.SHOW_ONLY,
            TourStepId.CLICK_NOTIFICATIONS to TourStepType.TARGET_CLICK,
            TourStepId.SOUND_SWITCH_INFO to TourStepType.SHOW_ONLY,
            TourStepId.CLICK_SOUND_LIBRARY to TourStepType.TARGET_CLICK,
            TourStepId.PREVIEW_SOUND to TourStepType.TARGET_CLICK,
            TourStepId.CHOOSE_SOUND to TourStepType.USER_CHOICE,
            TourStepId.HIDE_RESTORE_INFO to TourStepType.SHOW_ONLY,
            TourStepId.BACK_FROM_SOUND to TourStepType.NAV_BACK,
            TourStepId.CHOOSE_DEFER to TourStepType.USER_CHOICE,
            TourStepId.SYSTEM_NOTIFICATIONS_INFO to TourStepType.SHOW_ONLY,
            TourStepId.FINISHED to TourStepType.SHOW_ONLY,
        )
        assertEquals(expected.map { it.first }, definition.steps.map { it.id })
        assertEquals(expected.map { it.second }, definition.steps.map { it.type })
    }

    @Test
    fun coreDefinition_routeDependentSkipNav() {
        val byId = definition.steps.associateBy { it.id }
        assertEquals(TourNavAction.NAVIGATE_ARCHIVE, byId.getValue(TourStepId.CLICK_ARCHIVE).onSkipNav)
        assertEquals(
            TourNavAction.NAVIGATE_ARCHIVE_DAYS,
            byId.getValue(TourStepId.CLICK_ARCHIVE_DAYS).onSkipNav,
        )
        assertEquals(TourNavAction.POP_ONCE, byId.getValue(TourStepId.BACK_FROM_DAYS).onSkipNav)
        assertEquals(TourNavAction.POP_TO_HOME, byId.getValue(TourStepId.BACK_FROM_ARCHIVE).onSkipNav)
        assertEquals(TourNavAction.NAVIGATE_SETTINGS, byId.getValue(TourStepId.CLICK_SETTINGS).onSkipNav)
        assertEquals(
            TourNavAction.NAVIGATE_NOTIFICATIONS,
            byId.getValue(TourStepId.CLICK_NOTIFICATIONS).onSkipNav,
        )
        assertEquals(
            TourNavAction.NAVIGATE_SOUND_LIBRARY,
            byId.getValue(TourStepId.CLICK_SOUND_LIBRARY).onSkipNav,
        )
        assertEquals(TourNavAction.POP_ONCE, byId.getValue(TourStepId.BACK_FROM_SOUND).onSkipNav)
    }

    @Test
    fun coreDefinition_archiveDaysContent_hasStableTarget() {
        val step = definition.steps.first { it.id == TourStepId.ARCHIVE_DAYS_CONTENT }
        assertEquals(TourTargetId.ARCHIVE_DAYS_CONTENT, step.targetId)
        assertEquals(Routes.ARCHIVE_DAYS, step.expectedRoutePrefix)
        assertEquals(TourCompletion.ManualAdvance, step.completion)
    }

    @Test
    fun coreDefinition_exportAccordionThenExportShareTargets() {
        val byId = definition.steps.associateBy { it.id }
        val accordion = byId.getValue(TourStepId.CLICK_EXPORT_ACCORDION)
        assertEquals(TourTargetId.ARCHIVE_EXPORT_ACCORDION, accordion.targetId)
        assertEquals(TourCompletion.TargetActivation, accordion.completion)
        assertEquals(TourNavAction.NONE, accordion.onSkipNav)

        val export = byId.getValue(TourStepId.EXPORT_INFO)
        assertEquals(TourStepType.SHOW_ONLY, export.type)
        assertEquals(TourTargetId.ARCHIVE_EXPORT_SECTION, export.targetId)

        val share = byId.getValue(TourStepId.SHARE_INFO)
        assertEquals(TourStepType.SHOW_ONLY, share.type)
        assertEquals(TourTargetId.ARCHIVE_SHARE_SECTION, share.targetId)
    }

    @Test
    fun coreDefinition_soundTargetsAreDynamicBuiltinNotSystemDefault() {
        val byId = definition.steps.associateBy { it.id }
        assertEquals(
            TourTargetId.SOUND_LIBRARY_FIRST_VISIBLE_BUILTIN_PLAY,
            byId.getValue(TourStepId.PREVIEW_SOUND).targetId,
        )
        assertEquals(
            TourTargetId.SOUND_LIBRARY_FIRST_VISIBLE_BUILTIN_ITEM,
            byId.getValue(TourStepId.CHOOSE_SOUND).targetId,
        )
        assertEquals(
            TourTargetId.SOUND_HIDE_RESTORE_INFO,
            byId.getValue(TourStepId.HIDE_RESTORE_INFO).targetId,
        )
        assertEquals(TourStepType.SHOW_ONLY, byId.getValue(TourStepId.HIDE_RESTORE_INFO).type)
    }

    @Test
    fun coreDefinition_mutationStepsAreOnlyWordingSoundDefer() {
        val mutating = definition.steps.filter { it.type == TourStepType.USER_CHOICE }
        assertEquals(
            listOf(
                TourStepId.CHOOSE_WORDING,
                TourStepId.CHOOSE_SOUND,
                TourStepId.CHOOSE_DEFER,
            ),
            mutating.map { it.id },
        )
    }

    @Test
    fun coreDefinition_showOnlyBlocksHaveTargetsForSensitiveAreas() {
        val byId = definition.steps.associateBy { it.id }
        assertNotNull(byId.getValue(TourStepId.SCHEDULE_INFO).targetId)
        assertNotNull(byId.getValue(TourStepId.PAUSE_INFO).targetId)
        assertNotNull(byId.getValue(TourStepId.BACKUP_INFO).targetId)
        assertNotNull(byId.getValue(TourStepId.SOUND_SWITCH_INFO).targetId)
        assertNotNull(byId.getValue(TourStepId.EXPORT_INFO).targetId)
        assertNotNull(byId.getValue(TourStepId.SHARE_INFO).targetId)
        assertNotNull(byId.getValue(TourStepId.SYSTEM_NOTIFICATIONS_INFO).targetId)
        assertTrue(byId.getValue(TourStepId.SCHEDULE_INFO).type == TourStepType.SHOW_ONLY)
        assertTrue(byId.getValue(TourStepId.EXPORT_INFO).type == TourStepType.SHOW_ONLY)
        assertFalse(byId.getValue(TourStepId.CHOOSE_WORDING).type == TourStepType.SHOW_ONLY)
    }

    @Test
    fun coreDefinition_everyStepHasShortTitleMetadata() {
        definition.steps.forEach { step ->
            assertTrue(
                "Missing short title for ${step.id}",
                step.shortTitleResId != 0,
            )
        }
    }

    @Test
    fun exitStepNumbers_matchDefinitionOrder() {
        assertEquals(1, exitStepNumberInDefinition("START_INTRO", definition))
        assertEquals(21, exitStepNumberInDefinition("PREVIEW_SOUND", definition))
        assertEquals(27, exitStepNumberInDefinition("FINISHED", definition))
    }
}
