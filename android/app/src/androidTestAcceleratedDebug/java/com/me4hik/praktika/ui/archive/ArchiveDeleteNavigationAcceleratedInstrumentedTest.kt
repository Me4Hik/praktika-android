// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - navigation Compose tests delete
package com.me4hik.praktika.ui.archive

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
class ArchiveDeleteNavigationAcceleratedInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PracticeComposeTestActivity>()

    private lateinit var harness: AcceleratedUiTestHarness
    private val zone = ZoneId.of("Europe/Kiev")
    private var nextAnswerId: Long = 0L

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
    fun dayArchiveDeleteCancelKeepsEntry() {
        val day = LocalDate.of(2026, 8, 7)
        val answerId = seedDayWithTwoAnswers(day)
        setContent()
        openDayArchive(day)
        composeRule.onNodeWithText("Second answer").assertIsDisplayed()
        ArchiveComposeTestSupport.clickDeleteButton(composeRule, answerId)
        ArchiveComposeTestSupport.waitForDeleteDialog(composeRule)
        ArchiveComposeTestSupport.clickDeleteCancel(composeRule)
        composeRule.onNodeWithText("Second answer").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.DELETE_DIALOG).assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun dayArchiveDeleteConfirmRemovesEntryAndStaysOnScreen() {
        val day = LocalDate.of(2026, 8, 7)
        val answerId = seedDayWithTwoAnswers(day)
        setContent()
        openDayArchive(day)
        ArchiveComposeTestSupport.clickDeleteButton(composeRule, answerId)
        ArchiveComposeTestSupport.waitForDeleteDialog(composeRule)
        ArchiveComposeTestSupport.confirmDelete(composeRule, answerId)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText("Second answer").assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("First answer").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.DAY_SCREEN).assertIsDisplayed()
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        composeRule.onNodeWithTag("${ArchiveTestTags.DATE_ITEM_PREFIX}${day.toEpochDay()}").assertIsDisplayed()
    }

    @Test
    fun questionHistoryDeleteConfirmRemovesSelectedEntryOnly() {
        val day = LocalDate.of(2026, 8, 7)
        val (answerOneId, answerTwoId) = seedQuestionHistoryTwoAnswers(day)
        setContent()
        openQuestionHistory()
        composeRule.onNodeWithText("History answer 1").assertIsDisplayed()
        composeRule.onNodeWithText("History answer 2").assertIsDisplayed()
        ArchiveComposeTestSupport.clickDeleteButton(composeRule, answerTwoId)
        ArchiveComposeTestSupport.waitForDeleteDialog(composeRule)
        ArchiveComposeTestSupport.confirmDelete(composeRule, answerTwoId)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText("History answer 2").assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("History answer 1").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_SCREEN).assertIsDisplayed()
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        composeRule.onNodeWithText("Ответов: 1").assertIsDisplayed()
        runBlocking {
            assert(harness.database.answerDao().getById(answerOneId) != null)
            assert(harness.database.answerDao().getById(answerTwoId) == null)
        }
    }

    private fun openDayArchive(day: LocalDate) {
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveDate(composeRule, day.toEpochDay())
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
    }

    private fun openQuestionHistory() {
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesScreen(composeRule)
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, questionId = 1)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
    }

    private fun seedDayWithTwoAnswers(day: LocalDate): Long {
        var secondAnswerId = 0L
        runBlocking {
            harness.setUp()
            harness.startPractice()
            seedAnswer(
                cyclePosition = 2,
                questionText = "First question",
                answerText = "First answer",
                createdAt = epochMillis(day, 10, 0),
            )
            secondAnswerId = seedAnswer(
                cyclePosition = 3,
                questionText = "Second question",
                answerText = "Second answer",
                createdAt = epochMillis(day, 12, 0),
            )
        }
        return secondAnswerId
    }

    private fun seedQuestionHistoryTwoAnswers(day: LocalDate): Pair<Long, Long> {
        var firstId = 0L
        var secondId = 0L
        runBlocking {
            harness.setUp()
            harness.startPractice()
            firstId = seedAnswer(
                cyclePosition = 2,
                cycleNumber = 1,
                questionText = "History snapshot 1",
                answerText = "History answer 1",
                createdAt = epochMillis(day, 10, 0),
            )
            secondId = seedAnswer(
                cyclePosition = 3,
                cycleNumber = 2,
                questionText = "History snapshot 2",
                answerText = "History answer 2",
                createdAt = epochMillis(day, 18, 0),
            )
        }
        return firstId to secondId
    }

    private suspend fun seedAnswer(
        cyclePosition: Int,
        questionText: String,
        answerText: String,
        createdAt: Long,
        cycleNumber: Int = 1,
    ): Long {
        val occurrenceId = harness.database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
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
        return harness.database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = answerText,
                createdAtEpochMillis = createdAt,
            ),
        ).also { nextAnswerId = it }
    }

    private fun setContent() {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
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

    private fun epochMillis(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
