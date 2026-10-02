package com.me4hik.praktika.ui.tour

import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.R
import com.me4hik.praktika.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExperimentalCoreTourV1Test {

    private val definition = ExperimentalCoreTourV1.definition

    @Test
    fun actionCore_hasExactlyFiveTasks_inOrder() {
        assertEquals(
            listOf(
                TourTaskId.ARCHIVE_DAYS,
                TourTaskId.SCHEDULE,
                TourTaskId.WORDING,
                TourTaskId.SOUND,
                TourTaskId.DEFER,
            ),
            definition.actionTaskOrder,
        )
        assertEquals(5, definition.actionTaskCount)
    }

    @Test
    fun definition_stepOrderAndPhases() {
        val expected = listOf(
            TourStepId.START_INTRO to TourStepType.SHOW_ONLY,
            TourStepId.CLICK_ARCHIVE to TourStepType.TARGET_CLICK,
            TourStepId.CLICK_ARCHIVE_DAYS to TourStepType.TARGET_CLICK,
            TourStepId.ARCHIVE_DAYS_CONTENT to TourStepType.SHOW_ONLY,
            TourStepId.CLICK_SETTINGS to TourStepType.TARGET_CLICK,
            TourStepId.SCHEDULE_INFO to TourStepType.USER_CHOICE,
            TourStepId.CHOOSE_WORDING to TourStepType.USER_CHOICE,
            TourStepId.CLICK_NOTIFICATIONS to TourStepType.TARGET_CLICK,
            TourStepId.CLICK_SOUND_LIBRARY to TourStepType.TARGET_CLICK,
            TourStepId.CHOOSE_SOUND to TourStepType.USER_CHOICE,
            TourStepId.CHOOSE_DEFER to TourStepType.USER_CHOICE,
            TourStepId.PHASE_GATE to TourStepType.GATE,
            TourStepId.OVERVIEW_HOME to TourStepType.SHOW_ONLY,
            TourStepId.OVERVIEW_ARCHIVE to TourStepType.SHOW_ONLY,
            TourStepId.OVERVIEW_EXPORT_SHARE to TourStepType.SHOW_ONLY,
            TourStepId.OVERVIEW_PAUSE_BACKUP to TourStepType.SHOW_ONLY,
            TourStepId.TOUR_COMPLETION to TourStepType.SHOW_ONLY,
        )
        assertEquals(expected.map { it.first }, definition.steps.map { it.id })
        assertEquals(expected.map { it.second }, definition.steps.map { it.type })
        assertEquals(17, definition.steps.size)
    }

    @Test
    fun archiveScheduleSound_groupedAsSingleTasks() {
        val byTask = definition.steps.filter { it.taskId != null }.groupBy { it.taskId }
        assertEquals(
            listOf(
                TourStepId.CLICK_ARCHIVE,
                TourStepId.CLICK_ARCHIVE_DAYS,
                TourStepId.ARCHIVE_DAYS_CONTENT,
            ),
            byTask.getValue(TourTaskId.ARCHIVE_DAYS).map { it.id },
        )
        assertEquals(
            listOf(TourStepId.CLICK_SETTINGS, TourStepId.SCHEDULE_INFO),
            byTask.getValue(TourTaskId.SCHEDULE).map { it.id },
        )
        assertEquals(
            listOf(
                TourStepId.CLICK_NOTIFICATIONS,
                TourStepId.CLICK_SOUND_LIBRARY,
                TourStepId.CHOOSE_SOUND,
            ),
            byTask.getValue(TourTaskId.SOUND).map { it.id },
        )
    }

    @Test
    fun activeFlow_omitsLegacyBackAndInfoSteps() {
        val ids = definition.steps.map { it.id }.toSet()
        val removed = listOf(
            TourStepId.GO_HOME,
            TourStepId.HOME_STATUS,
            TourStepId.ARCHIVE_HUB_INTRO,
            TourStepId.BACK_FROM_DAYS,
            TourStepId.CLICK_EXPORT_ACCORDION,
            TourStepId.EXPORT_INFO,
            TourStepId.SHARE_INFO,
            TourStepId.BACK_FROM_ARCHIVE,
            TourStepId.PAUSE_INFO,
            TourStepId.BACKUP_INFO,
            TourStepId.SOUND_SWITCH_INFO,
            TourStepId.PREVIEW_SOUND,
            TourStepId.HIDE_RESTORE_INFO,
            TourStepId.BACK_FROM_SOUND,
            TourStepId.SYSTEM_NOTIFICATIONS_INFO,
            TourStepId.FINISHED,
        )
        removed.forEach { assertFalse("$it still in active flow", ids.contains(it)) }
        assertTrue(ids.contains(TourStepId.ARCHIVE_DAYS_CONTENT))
    }

    @Test
    fun chooseSound_usesAnyItemTarget() {
        val step = definition.steps.first { it.id == TourStepId.CHOOSE_SOUND }
        assertEquals(TourTargetId.SOUND_LIBRARY_ANY_ITEM, step.targetId)
        assertEquals(TourCompletion.TargetActivation, step.completion)
        assertEquals(TourNavAction.POP_ONCE, step.onCompleteNav)
    }

    @Test
    fun archiveTask_threeInternals_popOnlyAfterInfoNext() {
        val byId = definition.steps.associateBy { it.id }
        val oneA = byId.getValue(TourStepId.CLICK_ARCHIVE)
        val oneB = byId.getValue(TourStepId.CLICK_ARCHIVE_DAYS)
        val oneC = byId.getValue(TourStepId.ARCHIVE_DAYS_CONTENT)

        assertEquals(R.string.tour_task_archive, oneA.tipResId)
        assertEquals(TourCompletion.RouteMatch(Routes.ARCHIVE), oneA.completion)
        assertEquals(TourNavAction.POP_TO_HOME, oneA.onEnterNav)
        assertEquals(TourNavAction.POP_TO_HOME, oneA.onSkipNav)

        assertEquals(R.string.tour_task_archive_days, oneB.tipResId)
        assertEquals(TourCompletion.RouteMatch(Routes.ARCHIVE_DAYS), oneB.completion)
        assertEquals(TourNavAction.NONE, oneB.onCompleteNav)
        assertEquals(TourNavAction.POP_TO_HOME, oneB.onSkipNav)

        assertEquals(TourStepType.SHOW_ONLY, oneC.type)
        assertEquals(R.string.tour_task_archive_days_info, oneC.tipResId)
        assertEquals(TourTargetId.ARCHIVE_DAYS_CONTENT, oneC.targetId)
        assertEquals(Routes.ARCHIVE_DAYS, oneC.expectedRoutePrefix)
        assertEquals(TourCompletion.ManualAdvance, oneC.completion)
        assertEquals(TourNavAction.POP_TO_HOME, oneC.onCompleteNav)
        assertEquals(TourTaskId.ARCHIVE_DAYS, oneC.taskId)
        assertFalse(oneC.requiresActionCue())
        assertFalse(oneC.showsChromeSkip())
        assertTrue(oneA.showsChromeSkip())
        assertTrue(oneB.showsChromeSkip())
    }

    @Test
    fun overview_hasFourScreens_andCompositeNavToSettings() {
        assertEquals(4, definition.overviewSteps.size)
        val pauseBackup = definition.steps.first { it.id == TourStepId.OVERVIEW_PAUSE_BACKUP }
        assertEquals(
            listOf(TourNavAction.POP_TO_HOME, TourNavAction.NAVIGATE_SETTINGS),
            pauseBackup.resolvedEnterNavActions(),
        )
        assertEquals(TourTargetId.SETTINGS_PAUSE_BACKUP, pauseBackup.targetId)
        assertEquals(TourStepId.OVERVIEW_PAUSE_BACKUP, definition.overviewSteps.last().id)
    }

    @Test
    fun progressDisplay_taskOrdinalStableAcrossInternalSteps() {
        val sessionArchive1 = TourSessionState(
            status = TourStatus.Active,
            definitionId = definition.id,
            steps = definition.steps,
            stepIndex = definition.steps.indexOfFirst { it.id == TourStepId.CLICK_ARCHIVE },
        )
        val sessionArchive2 = sessionArchive1.copy(
            stepIndex = definition.steps.indexOfFirst { it.id == TourStepId.CLICK_ARCHIVE_DAYS },
        )
        val sessionArchive3 = sessionArchive1.copy(
            stepIndex = definition.steps.indexOfFirst { it.id == TourStepId.ARCHIVE_DAYS_CONTENT },
        )
        val sessionSchedule = sessionArchive1.copy(
            stepIndex = definition.steps.indexOfFirst { it.id == TourStepId.CLICK_SETTINGS },
        )
        val sessionDefer = sessionArchive1.copy(
            stepIndex = definition.steps.indexOfFirst { it.id == TourStepId.CHOOSE_DEFER },
        )
        val sessionGate = sessionArchive1.copy(
            stepIndex = definition.steps.indexOfFirst { it.id == TourStepId.PHASE_GATE },
        )
        val sessionOverview = sessionArchive1.copy(
            stepIndex = definition.steps.indexOfFirst { it.id == TourStepId.OVERVIEW_HOME },
        )
        assertEquals(TourProgressDisplay.Action(1, 5), sessionArchive1.progressDisplay)
        assertEquals(TourProgressDisplay.Action(1, 5), sessionArchive2.progressDisplay)
        assertEquals(TourProgressDisplay.Action(1, 5), sessionArchive3.progressDisplay)
        assertEquals(TourProgressDisplay.Action(2, 5), sessionSchedule.progressDisplay)
        assertEquals(TourProgressDisplay.Action(5, 5), sessionDefer.progressDisplay)
        assertEquals(TourProgressDisplay.Gate, sessionGate.progressDisplay)
        assertEquals(TourProgressDisplay.Overview(1, 4), sessionOverview.progressDisplay)
    }

    @Test
    fun actionCue_onlyOnActionableSteps() {
        val action = definition.steps.first { it.id == TourStepId.CLICK_ARCHIVE }
        val intro = definition.steps.first { it.id == TourStepId.START_INTRO }
        val gate = definition.steps.first { it.id == TourStepId.PHASE_GATE }
        val overview = definition.steps.first { it.id == TourStepId.OVERVIEW_HOME }
        assertTrue(action.requiresActionCue())
        assertFalse(intro.requiresActionCue())
        assertFalse(gate.requiresActionCue())
        assertFalse(overview.requiresActionCue())
    }

    @Test
    fun startIntroCopy_isCapabilityTone_withoutProhibitiveMeta() {
        val tip = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.tour_step_start_intro)
        assertTrue(tip.contains("Здравствуйте"))
        assertTrue(tip.contains("Если какой-то шаг уже знаком, его можно пропустить."))
        assertFalse(tip.contains("Ненужную"))
        assertFalse(tip.contains("Сейчас не"))
        assertFalse(tip.contains("В обучении не"))
        assertFalse(tip.contains("туда не переходим"))
    }

    @Test
    fun task1_internalTips_areSplit() {
        val byId = definition.steps.associateBy { it.id }
        assertEquals(R.string.tour_task_archive, byId.getValue(TourStepId.CLICK_ARCHIVE).tipResId)
        assertEquals(R.string.tour_task_archive_days, byId.getValue(TourStepId.CLICK_ARCHIVE_DAYS).tipResId)
        assertEquals(
            R.string.tour_task_archive_days_info,
            byId.getValue(TourStepId.ARCHIVE_DAYS_CONTENT).tipResId,
        )
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Откройте Архив ответов.", ctx.getString(R.string.tour_task_archive))
        assertEquals("Теперь откройте Архив по дням.", ctx.getString(R.string.tour_task_archive_days))
        assertEquals(
            "Здесь будут собраны ваши ответы по дням. Нажмите „Далее“.",
            ctx.getString(R.string.tour_task_archive_days_info),
        )
    }

    @Test
    fun task2AndTask4_internalTips_areSplit() {
        val byId = definition.steps.associateBy { it.id }
        assertEquals(R.string.tour_task_schedule, byId.getValue(TourStepId.CLICK_SETTINGS).tipResId)
        assertEquals(R.string.tour_task_schedule_change, byId.getValue(TourStepId.SCHEDULE_INFO).tipResId)
        assertEquals(
            R.string.tour_task_sound_notifications,
            byId.getValue(TourStepId.CLICK_NOTIFICATIONS).tipResId,
        )
        assertEquals(
            R.string.tour_task_sound_library,
            byId.getValue(TourStepId.CLICK_SOUND_LIBRARY).tipResId,
        )
        assertEquals(R.string.tour_task_sound, byId.getValue(TourStepId.CHOOSE_SOUND).tipResId)
    }

    @Test
    fun phaseB_overview_usesManualAdvance_forNextAndDonePattern() {
        val overview = definition.overviewSteps
        assertEquals(4, overview.size)
        overview.forEach {
            assertEquals(
                "overview ${it.id} should ManualAdvance for Далее/Готово",
                TourCompletion.ManualAdvance,
                it.completion,
            )
        }
        assertEquals(TourStepId.OVERVIEW_PAUSE_BACKUP, overview.last().id)
        val b4Index = definition.steps.indexOfFirst { it.id == TourStepId.OVERVIEW_PAUSE_BACKUP }
        assertEquals(TourStepId.TOUR_COMPLETION, definition.steps[b4Index + 1].id)
    }

    @Test
    fun phaseB_copy_avoidsProhibitivePhrases() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(
            R.string.tour_overview_home,
            R.string.tour_overview_archive,
            R.string.tour_overview_export_share,
            R.string.tour_overview_pause_backup,
            R.string.tour_step_phase_gate,
        ).forEach { res ->
            val text = ctx.getString(res)
            assertFalse(text.contains("Сейчас не"))
            assertFalse(text.contains("Сейчас ничего не"))
            assertFalse(text.contains("В обучении не"))
            assertFalse(text.contains("В обучении ничего не"))
            assertFalse(text.contains("туда не переходим"))
            assertFalse(text.contains("файлы не создаём"))
            assertFalse(text.contains("системное окно не открываем"))
        }
    }

    @Test
    fun phaseB_b1_and_b4_exactCopy() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(
            "На главном экране видно состояние Практики. Отсюда открываются Архив и Настройки.",
            ctx.getString(R.string.tour_overview_home),
        )
        assertEquals(
            "В Архиве собраны ответы по дням, вопросы, закономерности и экспорт.",
            ctx.getString(R.string.tour_overview_archive),
        )
        assertEquals(
            "Ответы можно сохранить как Markdown, CSV, PDF или Excel и поделиться ими.",
            ctx.getString(R.string.tour_overview_export_share),
        )
        assertEquals(
            "Практику можно поставить на паузу и снова возобновить. Чтобы не потерять ответы и прогресс, создайте резервную копию в выбранной папке. Из неё данные можно восстановить после переустановки или сбоя.",
            ctx.getString(R.string.tour_overview_pause_backup),
        )
        assertFalse(ctx.getString(R.string.tour_overview_home).contains("время следующего"))
        assertFalse(ctx.getString(R.string.tour_overview_home).contains("паузу"))
        assertFalse(ctx.getString(R.string.tour_overview_pause_backup).contains("при необходимости"))
        assertFalse(ctx.getString(R.string.tour_overview_pause_backup).contains("все данные"))
        assertFalse(ctx.getString(R.string.tour_overview_pause_backup).contains("все настройки"))
    }

    @Test
    fun mutatingUserChoices_includeScheduleWordingSoundDefer() {
        val mutating = definition.steps.filter { it.type == TourStepType.USER_CHOICE }
        assertEquals(
            listOf(
                TourStepId.SCHEDULE_INFO,
                TourStepId.CHOOSE_WORDING,
                TourStepId.CHOOSE_SOUND,
                TourStepId.CHOOSE_DEFER,
            ),
            mutating.map { it.id },
        )
    }

    @Test
    fun everyStepHasShortTitleMetadata() {
        definition.steps.forEach { step ->
            assertTrue("Missing short title for ${step.id}", step.shortTitleResId != 0)
            assertNotNull(step.tipResId)
        }
    }

    @Test
    fun exitStepNumbers_matchDefinitionOrder() {
        assertEquals(1, exitStepNumberInDefinition("START_INTRO", definition))
        assertEquals(2, exitStepNumberInDefinition("CLICK_ARCHIVE", definition))
        assertEquals(4, exitStepNumberInDefinition("ARCHIVE_DAYS_CONTENT", definition))
        assertEquals(10, exitStepNumberInDefinition("CHOOSE_SOUND", definition))
        assertEquals(12, exitStepNumberInDefinition("PHASE_GATE", definition))
        assertEquals(17, exitStepNumberInDefinition("TOUR_COMPLETION", definition))
        assertNull(
            definition.steps.firstOrNull { it.id == TourStepId.PREVIEW_SOUND },
        )
    }
}
