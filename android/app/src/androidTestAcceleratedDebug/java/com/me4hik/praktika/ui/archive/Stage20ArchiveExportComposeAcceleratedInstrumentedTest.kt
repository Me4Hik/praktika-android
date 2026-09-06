// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - Compose format dialog tests
package com.me4hik.praktika.ui.archive

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class Stage20ArchiveExportComposeAcceleratedInstrumentedTest {
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
    fun exportAllShowsFormatDialog() {
        setContent(started = true)
        openArchive()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_MARKDOWN).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_CSV).assertIsDisplayed()
    }

    @Test
    fun formatDialogCancelDoesNothing() {
        setContent(started = true)
        openArchive()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_CANCEL).performClick()
        composeRule.onNodeWithText("Файл сохранён").assertDoesNotExist()
        composeRule.onNodeWithText("Нет ответов для экспорта").assertDoesNotExist()
    }

    @Test
    fun dayExportShowsFormatDialog() {
        val day = LocalDate.of(2026, 8, 7)
        setContent(started = true, seedArchiveData = {
            seedAnswer(
                cyclePosition = 2,
                questionText = "Day export question",
                answerText = "Day export answer",
                createdAt = epochMillis(day, 12, 0),
            )
        })
        openArchive()
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveDate(composeRule, day.toEpochDay())
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_DAY).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_DIALOG).assertIsDisplayed()
    }

    @Test
    fun questionExportShowsFormatDialog() {
        val day = LocalDate.of(2026, 8, 7)
        setContent(started = true, seedArchiveData = {
            seedAnswer(
                questionId = 1,
                cyclePosition = 2,
                questionText = "History export question",
                answerText = "History export answer",
                createdAt = epochMillis(day, 12, 0),
            )
        })
        openArchive()
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, questionId = 1)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_HISTORY).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_DIALOG).assertIsDisplayed()
    }

    @Test
    fun periodExportShowsFormatDialogAfterRangeSelection() {
        val rangeStart = LocalDate.of(2026, 8, 1)
        val rangeEnd = LocalDate.of(2026, 8, 7)
        setContent(
            started = true,
            testPeriodExportStartEpochDay = rangeStart.toEpochDay(),
            testPeriodExportEndEpochDay = rangeEnd.toEpochDay(),
        )
        openArchive()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_CONFIRM).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_DIALOG).assertIsDisplayed()
    }

    @Test
    fun markdownChoiceTriggersExportFlow() {
        setContent(started = true)
        openArchive()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_MARKDOWN).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText("Нет ответов для экспорта").assertExists()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun csvChoiceTriggersExportFlow() {
        setContent(started = true)
        openArchive()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_CSV).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText("Нет ответов для экспорта").assertExists()
                true
            }.getOrDefault(false)
        }
    }

    private fun openArchive() {
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.expandArchiveExportShare(composeRule)
    }

    private fun setContent(
        started: Boolean = false,
        seedArchiveData: (suspend AcceleratedUiTestHarness.() -> Unit)? = null,
        testPeriodExportStartEpochDay: Long? = null,
        testPeriodExportEndEpochDay: Long? = null,
    ) {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        runBlocking {
            harness.setUp()
            if (started || seedArchiveData != null) {
                harness.startPractice()
            }
            seedArchiveData?.invoke(harness)
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
                    testPeriodExportStartEpochDay = testPeriodExportStartEpochDay,
                    testPeriodExportEndEpochDay = testPeriodExportEndEpochDay,
                )
            }
        }
        if (started || seedArchiveData != null) {
            PracticeComposeTestSupport.waitForHome(composeRule)
        }
    }

    private suspend fun AcceleratedUiTestHarness.seedAnswer(
        questionId: Int = 1,
        cyclePosition: Int,
        questionText: String,
        answerText: String,
        createdAt: Long,
    ) {
        val occurrenceId = database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = questionText,
                cycleNumber = 1,
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
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
