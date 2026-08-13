// 10.08.2026 Post-release fixes cursor by Me4Hik START - targeted answer trace accelerated proof
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.Stage10BlackviewE2ESupport
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class TargetedAnswerTraceAcceleratedInstrumentedTest {
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
    fun answerSaveSuccessAndBlankFailureEmitTargetedTraceEvents() {
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        runBlocking { runtime.cycleRepository.startPractice() }
        runBlocking { Stage10BlackviewE2ESupport.stepEvent(runtime) }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
        }

        val occurrenceId = runBlocking {
            val occurrence = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
            assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
            occurrence.id
        }
        val seqBefore = TargetedTraceTestSupport.readEvents(context).maxOfOrNull { it.seq } ?: 0L

        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("targeted trace answer")
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).assertIsEnabled().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                runtime.database.questionOccurrenceDao().getById(occurrenceId)?.status ==
                    QuestionOccurrenceStatus.ANSWERED
            }
        }

        val successAttempt = TargetedTraceTestSupport.waitForEventAfter(
            context = context,
            name = "answer_save_attempt",
            afterSeq = seqBefore,
            predicate = { it.metadata["draft_blank"] == "false" },
        )
        checkNotNull(successAttempt)
        assertEquals(occurrenceId.toString(), successAttempt.metadata["occurrence_id"])

        val successResult = TargetedTraceTestSupport.waitForEventAfter(
            context = context,
            name = "answer_save_result",
            afterSeq = seqBefore,
            predicate = { it.metadata["result"] == "success" },
        )
        checkNotNull(successResult)
        assertEquals(occurrenceId.toString(), successResult.metadata["occurrence_id"])
        assertEquals("SUCCESS", successResult.metadata["reason_enum"])

        val screenOpens = TargetedTraceTestSupport.readEvents(context)
            .filter { it.name == "screen_open" && it.seq > seqBefore }
        assertTrue(screenOpens.any { it.metadata["route"]?.contains("question") == true })
        assertTrue(screenOpens.any { it.metadata["route"]?.contains("answer") == true })
        // NavController reports template routes (question/{occurrenceId}); occurrence identity is proven via save trace.

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        TargetedTraceTestSupport.submitManualReport(
            composeRule = composeRule,
            comment = "TARGETED ANSWER TRACE TEST",
        )

        assertTrue(
            TargetedTraceTestSupport.reportContainsEventNames(
                context,
                "answer_save_attempt",
                "answer_save_result",
            ),
        )
        val report = TargetedTraceTestSupport.latestReport(context)!!
        assertFalse(report.has("question_text"))
        assertFalse(report.has("answer_text"))
        assertTrue(report.optString("testerComment").contains("TARGETED ANSWER TRACE TEST"))

        TargetedTraceTestSupport.exportDiagnosticsToExternal(context, "answer")
        TargetedTraceTestSupport.logPrompt124Trace(
            tag = "PROMPT124_ANSWER_TRACE",
            context = context,
            eventNames = setOf(
                "screen_open",
                "answer_save_attempt",
                "answer_save_result",
                "bug_report_submit",
            ),
        )

        // Blank failure path is UI-blocked (save disabled when draft empty); covered by AnswerViewModelTargetedTraceTest JVM proof.
    }

    private fun resetAcceleratedSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter {
                it.name == AcceleratedTimeStorage.STATE_FILE_NAME ||
                    it.name == "diagnostics"
            }
            ?.forEach { entry ->
                if (entry.isDirectory) {
                    entry.deleteRecursively()
                } else {
                    entry.delete()
                }
            }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
