// 05.08.2026 Answer Save cursor by Me4Hik START - MainActivity Answer draft recreation
package com.me4hik.praktika.ui.practice

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class MainActivityAnswerRecreationInstrumentedTest {
    private val devicePreflightRule = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
                base.evaluate()
            }
        }
    }

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
        .outerRule(devicePreflightRule)
        .around(sandboxReset)
        .around(composeRule)

    @Test
    fun answerDraftSurvivesActivityRecreationOnRealMainActivity() {
        val draftBeforeSave = "Да\n\nТекст после recreation 👣"
        runBlocking {
            MainActivityInitGate.awaitInit()
            PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
            composeRule.waitForIdle()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START)
            }

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val runtime = PraktikaRuntimeHolder.get(context)
            runtime.cycleRepository.startPractice()
            stepEvent(runtime)

            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
            }

            val occurrenceBefore = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
            val occurrenceId = occurrenceBefore.id
            assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrenceBefore.status)

            PracticeComposeTestSupport.clickHomeAnswer(composeRule)
            PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ANSWER_INPUT)
            }

            composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("Да")
            composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("\n")
            composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("\n")
            composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT)
                .performTextInput("Текст после recreation 👣")
            assertEditableTextEquals(PracticeTestTags.ANSWER_INPUT, draftBeforeSave)

            assertEquals(0, runtime.database.answerDao().count())
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runBlocking {
                    runtime.database.questionOccurrenceDao().getById(occurrenceId)?.openedAtEpochMillis != null
                }
            }

            composeRule.activityRule.scenario.recreate()

            MainActivityInitGate.awaitInit()
            PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
            composeRule.waitForIdle()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ANSWER_INPUT)
            }

            composeRule.onNodeWithTag(PracticeTestTags.ANSWER_QUESTION_TEXT).assertIsDisplayed()
            assertEditableTextEquals(PracticeTestTags.ANSWER_INPUT, draftBeforeSave)

            val occurrenceAfterRecreate = runtime.database.questionOccurrenceDao().getById(occurrenceId)!!
            assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrenceAfterRecreate.status)
            assertEquals(0, runtime.database.answerDao().count())
            assertNotNull(occurrenceAfterRecreate.openedAtEpochMillis)

            composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).assertIsEnabled()
            composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).performClick()
            composeRule.waitForIdle()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
            }
            composeRule.onNodeWithText("Вопрос 2 из 21").assertIsDisplayed()
            composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()

            val savedOccurrence = runtime.database.questionOccurrenceDao().getById(occurrenceId)!!
            val answer = runtime.database.answerDao().getByOccurrenceId(occurrenceId)!!
            assertEquals(QuestionOccurrenceStatus.ANSWERED, savedOccurrence.status)
            assertEquals(draftBeforeSave, answer.text)
            assertEquals(savedOccurrence.completedAtEpochMillis, answer.createdAtEpochMillis)
            assertEquals(1, runtime.database.answerDao().count())
            assertNotNull(savedOccurrence.openedAtEpochMillis)
            assertEquals(
                QuestionOccurrenceStatus.SCHEDULED,
                runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!.status,
            )
        }
    }

    private suspend fun stepEvent(runtime: com.me4hik.praktika.runtime.PraktikaRuntime) {
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        val target = when (incomplete.status) {
            QuestionOccurrenceStatus.SCHEDULED -> incomplete.plannedAtEpochMillis
            QuestionOccurrenceStatus.AVAILABLE -> incomplete.availableUntilEpochMillis
            else -> error("Occurrence is not steppable: ${incomplete.status}")
        }
        if (target > clock.currentVirtualNow()) {
            clock.advanceToVirtualEpochMillis(target)
        }
        runtime.cycleRepository.reconcile()
        clock.checkpoint()
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

    private fun assertEditableTextEquals(tag: String, expected: String) {
        val actual = composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
        assertEquals(expected, actual)
    }
}
// 05.08.2026 Answer Save cursor by Me4Hik END
