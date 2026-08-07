// 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - Blackview visual smoke
package com.me4hik.praktika.ui.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.archive.ArchiveComposeTestSupport
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class Stage17RepeatAnswerBlackviewSmokeInstrumentedTest {
    private val sandboxReset = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                resetAcceleratedSandbox()
                try {
                    base.evaluate()
                } finally {
                    PraktikaRuntimeHolder.resetForTests()
                    MainActivityInitGate.resetForTests()
                }
            }
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(sandboxReset)
        .around(composeRule)

    private val zone = ZoneId.of("Europe/Kiev")
    private var smokeCycleOneNumber = 1
    private var smokeCycleTwoNumber = 2

    @Before
    fun assumeOptIn() {
        assumeTrue(
            "stage17_repeat_smoke opt-in required",
            InstrumentationRegistry.getArguments().getString("stage17_repeat_smoke") == "true",
        )
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun repeatAnswerHistoryOfferSmoke() = runBlocking {
        MainActivityInitGate.awaitInit()
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitForIdle()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = PraktikaRuntimeHolder.get(context)
        if (runtime.database.practiceStateDao().get()?.isPracticeStarted != true) {
            runtime.cycleRepository.startPractice()
        }
        seedRepeatQuestionScenario(runtime)
        runtime.cycleRepository.reconcile()

        composeRule.activityRule.scenario.recreate()
        MainActivityInitGate.awaitInit()
        runtime.cycleRepository.reconcile()
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
        }

        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
        assertTrue(composeRule.onAllNodesWithText("Первый ответ").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("Новый независимый ответ")
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).performClick()
        composeRule.waitForIdle()
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Ответ сохранён").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()
        composeRule.onNodeWithText("Посмотреть историю").assertIsDisplayed().performClick()
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "Первый ответ")
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "Новый независимый ответ")
        ArchiveComposeTestSupport.assertQuestionHistoryCycleLabelDisplayed(composeRule, smokeCycleOneNumber)
        ArchiveComposeTestSupport.assertQuestionHistoryCycleLabelDisplayed(composeRule, smokeCycleTwoNumber)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.assertHomePositionDisplayed(composeRule)
        assertTrue(composeRule.onAllNodesWithTag(PracticeTestTags.ANSWER_INPUT).fetchSemanticsNodes().isEmpty())
    }

    private suspend fun seedRepeatQuestionScenario(runtime: com.me4hik.praktika.runtime.PraktikaRuntime) {
        val occurrenceDao = runtime.database.questionOccurrenceDao()
        occurrenceDao.getIncompleteOrdered().forEach { existing ->
            occurrenceDao.updateStatusIfCurrent(
                id = existing.id,
                expectedStatus = existing.status,
                newStatus = QuestionOccurrenceStatus.MISSED_BY_TIME,
                completedAtEpochMillis = existing.availableUntilEpochMillis,
            )
        }
        val now = runtime.timeProvider.nowEpochMillis()
        val plannedAt = now - 60_000L
        val availableUntil = now + 3_600_000L
        val completedAt = now - 30_000L
        val maxCycleNumber = occurrenceDao.getAllOrderedByPlannedAt()
            .maxOfOrNull { it.cycleNumber }
            ?: 0
        val cycleOneNumber = maxCycleNumber + 1
        val cycleTwoNumber = maxCycleNumber + 2
        smokeCycleOneNumber = cycleOneNumber
        smokeCycleTwoNumber = cycleTwoNumber
        val cycleOneOccurrenceId = occurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Smoke repeat question cycle 1",
                cycleNumber = cycleOneNumber,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = availableUntil,
                openedAtEpochMillis = null,
                completedAtEpochMillis = completedAt,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = zone.id,
            ),
        )
        runtime.database.answerDao().insert(
            AnswerEntity(
                occurrenceId = cycleOneOccurrenceId,
                text = "Первый ответ",
                createdAtEpochMillis = completedAt,
            ),
        )
        occurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Smoke repeat question cycle 2",
                cycleNumber = cycleTwoNumber,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = availableUntil,
                openedAtEpochMillis = null,
                completedAtEpochMillis = null,
                status = QuestionOccurrenceStatus.AVAILABLE,
                zoneId = zone.id,
            ),
        )
        runtime.database.practiceStateDao().update(
            runtime.database.practiceStateDao().get()!!.copy(
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = plannedAt,
                currentCycleNumber = cycleTwoNumber,
                nextCyclePosition = 2,
                lastProcessedAtEpochMillis = plannedAt,
                activeZoneId = zone.id,
            ),
        )
    }

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, 0, zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun resetAcceleratedSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }
            ?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }
}
// 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
