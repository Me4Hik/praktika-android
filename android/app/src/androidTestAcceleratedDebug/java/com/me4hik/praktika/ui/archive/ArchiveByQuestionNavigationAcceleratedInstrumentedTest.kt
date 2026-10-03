// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - navigation Compose tests
package com.me4hik.praktika.ui.archive

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.ui.practice.AcceleratedUiTestHarness
import com.me4hik.praktika.ui.practice.PracticeComposeTestActivity
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeRootViewModel
import com.me4hik.praktika.ui.practice.PracticeRootViewModelFactory
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveByQuestionNavigationAcceleratedInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PracticeComposeTestActivity>()

    private lateinit var harness: AcceleratedUiTestHarness
    private val zone = ZoneId.of("Europe/Kiev")

    @Before
    fun setUp() {
        harness = AcceleratedUiTestHarness(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) {
            harness.tearDown()
        }
    }

    @Test
    fun archiveDatesOpensQuestionsListWithCounts() {
        setContent(seedArchiveData = {
            seedQuestionAnswer(
                questionId = 1,
                cycleNumber = 1,
                cyclePosition = 2,
                questionText = "Question A latest",
                answerText = "Answer A1",
                createdAt = epochMillis(LocalDate.of(2026, 8, 5), 10, 0),
            )
            seedQuestionAnswer(
                questionId = 1,
                cycleNumber = 2,
                cyclePosition = 2,
                questionText = "Question A older",
                answerText = "Answer A2",
                createdAt = epochMillis(LocalDate.of(2026, 8, 7), 12, 0),
            )
            seedQuestionAnswer(
                questionId = 2,
                cycleNumber = 1,
                cyclePosition = 3,
                questionText = "Question B",
                answerText = "Answer B1",
                createdAt = epochMillis(LocalDate.of(2026, 8, 6), 15, 0),
            )
        })
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        composeRule.onNodeWithText("Question A older").assertIsDisplayed()
        composeRule.onNodeWithText("Question B").assertIsDisplayed()
        composeRule.onNodeWithText("Ответов: 2").assertIsDisplayed()
        composeRule.onNodeWithText("Ответов: 1").assertIsDisplayed()
    }

    @Test
    fun questionHistoryShowsSnapshotsAnswersDateTimeAndCycleInAscOrder() {
        val dayOne = LocalDate.of(2026, 8, 5)
        val dayTwo = LocalDate.of(2026, 8, 7)
        setContent(seedArchiveData = {
            seedQuestionAnswer(
                questionId = 1,
                cycleNumber = 1,
                cyclePosition = 2,
                questionText = "Snapshot A1",
                answerText = "Answer 1",
                createdAt = epochMillis(dayOne, 10, 0),
            )
            seedQuestionAnswer(
                questionId = 1,
                cycleNumber = 2,
                cyclePosition = 2,
                questionText = "Snapshot A2",
                answerText = "Answer 2\nSecond line",
                createdAt = epochMillis(dayTwo, 18, 42),
            )
        })
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, questionId = 1)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        composeRule.onNodeWithText("История ответов").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText("Snapshot A1"))
        composeRule.onNodeWithText("Snapshot A1").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText("Snapshot A2"))
        composeRule.onNodeWithText("Snapshot A2").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText("Answer 1"))
        composeRule.onNodeWithText("Answer 1").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText("Answer 2\nSecond line"))
        composeRule.onNodeWithText("Answer 2\nSecond line").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText("Цикл 1"))
        composeRule.onNodeWithText("Цикл 1").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText("Цикл 2"))
        composeRule.onNodeWithText("Цикл 2").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText("18:42", substring = true))
        composeRule.onNodeWithText("18:42", substring = true).assertIsDisplayed()
    }

    @Test
    fun backNavigationReturnsThroughQuestionsDatesAndHome() {
        setContent(seedArchiveData = {
            seedQuestionAnswer(
                questionId = 1,
                cycleNumber = 1,
                cyclePosition = 2,
                questionText = "Back question",
                answerText = "Back answer",
                createdAt = epochMillis(LocalDate.of(2026, 8, 7), 11, 0),
            )
        })
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, questionId = 1)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.assertHomeStartedContentDisplayed(composeRule)
    }

    @Test
    fun reopenSameQuestionHistoryAfterBack() {
        setContent(seedArchiveData = {
            seedQuestionAnswer(
                questionId = 1,
                cycleNumber = 1,
                cyclePosition = 2,
                questionText = "Persist question",
                answerText = "Persist answer",
                createdAt = epochMillis(LocalDate.of(2026, 8, 7), 9, 30),
            )
        })
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, questionId = 1)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, questionId = 1)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        composeRule.onNodeWithText("Persist answer").assertIsDisplayed()
    }

    private fun setContent(
        seedArchiveData: suspend AcceleratedUiTestHarness.() -> Unit,
    ) {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        runBlocking {
            harness.setUp()
            harness.startPractice()
            seedArchiveData.invoke(harness)
        }
        composeRule.setContent {
            PraktikaTheme {
                val factory = remember(harness.runtime, composeRule.activity) {
                    PracticeRootViewModelFactory(
                        owner = composeRule.activity,
                        runtime = harness.runtime,
                        onRequestPostNotifications = {},
                        onOpenAppNotificationSettings = {},
                        onOpenChannelSettings = {},
                    )
                }
                val rootViewModel: PracticeRootViewModel = viewModel(factory = factory)
                AppNavigation(
                    runtime = harness.runtime,
                    viewModel = rootViewModel,
                )
            }
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
    }

    private suspend fun AcceleratedUiTestHarness.seedQuestionAnswer(
        questionId: Int,
        cycleNumber: Int,
        cyclePosition: Int,
        questionText: String,
        answerText: String,
        createdAt: Long,
    ) {
        val occurrenceId = database.questionOccurrenceDao().insert(
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
        database.answerDao().insert(
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
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
