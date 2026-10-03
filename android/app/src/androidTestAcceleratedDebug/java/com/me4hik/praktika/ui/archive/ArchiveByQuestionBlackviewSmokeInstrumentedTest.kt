// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - Blackview visual smoke
package com.me4hik.praktika.ui.archive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
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
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class ArchiveByQuestionBlackviewSmokeInstrumentedTest {
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

    @Before
    fun assumeOptIn() {
        assumeTrue(
            "stage16_archive_smoke opt-in required",
            InstrumentationRegistry.getArguments().getString("stage16_archive_smoke") == "true",
        )
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun archiveByQuestionSmokeTwoQuestionsAndHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = PraktikaRuntimeHolder.get(context)
        val practiceState = runtime.database.practiceStateDao().get()
        if (practiceState?.isPracticeStarted != true) {
            runtime.cycleRepository.startPractice()
        }
        val nextPosition = runtime.database.questionOccurrenceDao()
            .getAllOrderedByPlannedAt()
            .maxOfOrNull { it.cyclePosition }
            ?.plus(1)
            ?: 2
        val dayOne = LocalDate.of(2026, 8, 5)
        val dayTwo = LocalDate.of(2026, 8, 7)
        seedQuestionAnswer(
            runtime = runtime,
            questionId = 1,
            cycleNumber = 1,
            cyclePosition = nextPosition,
            questionText = "Smoke snapshot A1",
            answerText = "Smoke answer 1",
            createdAt = epochMillis(dayOne, 9, 0),
        )
        seedQuestionAnswer(
            runtime = runtime,
            questionId = 1,
            cycleNumber = 2,
            cyclePosition = nextPosition + 1,
            questionText = "Smoke snapshot A2",
            answerText = "Smoke answer 2",
            createdAt = epochMillis(dayTwo, 18, 30),
        )
        seedQuestionAnswer(
            runtime = runtime,
            questionId = 2,
            cycleNumber = 1,
            cyclePosition = nextPosition + 2,
            questionText = "Smoke question B",
            answerText = "Smoke answer B",
            createdAt = epochMillis(dayTwo, 12, 0),
        )
        runtime.database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 2,
                questionTextSnapshot = "Skipped without answer",
                cycleNumber = 1,
                cyclePosition = nextPosition + 3,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochMillis(dayTwo, 13, 0) - 3_600_000,
                availableUntilEpochMillis = epochMillis(dayTwo, 13, 0) + 3_600_000,
                status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
                completedAtEpochMillis = epochMillis(dayTwo, 13, 0),
                zoneId = zone.id,
            ),
        )

        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ARCHIVE) ||
                PracticeComposeTestSupport.hasHomeStartedContent(composeRule)
        }

        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(composeRule, questionId = 1, "Smoke snapshot A2")
        ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(composeRule, questionId = 2, "Smoke question B")
        ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(composeRule, questionId = 1, "Ответов: 2")
        ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(composeRule, questionId = 2, "Ответов: 1")
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, questionId = 1)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "Smoke snapshot A1")
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "Smoke snapshot A2")
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "Smoke answer 1")
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "Smoke answer 2")
        ArchiveComposeTestSupport.assertQuestionHistoryCycleLabelDisplayed(composeRule, cycleNumber = 1)
        ArchiveComposeTestSupport.assertQuestionHistoryCycleLabelDisplayed(composeRule, cycleNumber = 2)
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "18:30", substring = true)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.assertHomeStartedContentDisplayed(composeRule)
    }

    private suspend fun seedQuestionAnswer(
        runtime: com.me4hik.praktika.runtime.PraktikaRuntime,
        questionId: Int,
        cycleNumber: Int,
        cyclePosition: Int,
        questionText: String,
        answerText: String,
        createdAt: Long,
    ) {
        val occurrenceId = runtime.database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = questionText,
                cycleNumber = cycleNumber,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = createdAt - 3_600_000,
                availableUntilEpochMillis = createdAt + 3_600_000,
                status = QuestionOccurrenceStatus.ANSWERED,
                completedAtEpochMillis = createdAt,
                zoneId = zone.id,
            ),
        )
        runtime.database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = answerText,
                createdAtEpochMillis = createdAt,
            ),
        )
    }

    private fun epochMillis(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

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
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
