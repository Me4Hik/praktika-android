// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - navigation Compose tests
package com.me4hik.praktika.ui.archive

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
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
import com.me4hik.praktika.ui.practice.PracticeTestTags
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
class ArchiveByDateNavigationAcceleratedInstrumentedTest {
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
    fun homeOpensArchiveEmptyState() {
        setContent(started = true)
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        ArchiveComposeTestSupport.assertArchiveEmpty(composeRule)
    }

    @Test
    fun archiveShowsMultipleDatesAndOpensDay() {
        val dayOne = LocalDate.of(2026, 8, 5)
        val dayTwo = LocalDate.of(2026, 8, 7)
        setContent(seedArchiveData = {
            seedAnswer(
                cyclePosition = 2,
                questionText = "Первый вопрос",
                answerText = "Первый ответ",
                createdAt = epochMillis(dayOne, 12, 0),
            )
            seedAnswer(
                cyclePosition = 3,
                questionText = "Второй вопрос",
                answerText = "Сегодня я чувствую опору спокойнее.\nСтопы уверенно касаются земли.",
                createdAt = epochMillis(dayTwo, 18, 42),
            )
        })
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag("${ArchiveTestTags.DATE_ITEM_PREFIX}${dayTwo.toEpochDay()}").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("${ArchiveTestTags.DATE_ITEM_PREFIX}${dayOne.toEpochDay()}").assertIsDisplayed()
        ArchiveComposeTestSupport.clickArchiveDate(composeRule, dayTwo.toEpochDay())
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        composeRule.onNodeWithText("Второй вопрос").assertIsDisplayed()
        composeRule.onNodeWithText("Сегодня я чувствую опору спокойнее.\nСтопы уверенно касаются земли.").assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.DAY_LIST)
            .performScrollToNode(hasText("18:42", substring = true))
        composeRule.onNodeWithText("18:42", substring = true).assertIsDisplayed()
    }

    @Test
    fun backNavigationReturnsToDatesThenHome() {
        val day = LocalDate.of(2026, 8, 7)
        setContent(seedArchiveData = {
            seedAnswer(
                cyclePosition = 2,
                questionText = "Вопрос дня",
                answerText = "Ответ дня",
                createdAt = epochMillis(day, 10, 0),
            )
        })
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveDate(composeRule, day.toEpochDay())
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.assertHomePositionDisplayed(composeRule)
    }

    @Test
    fun reopenSameDateFromList() {
        val day = LocalDate.of(2026, 8, 7)
        setContent(seedArchiveData = {
            seedAnswer(
                cyclePosition = 2,
                questionText = "Persist question",
                answerText = "Persist answer",
                createdAt = epochMillis(day, 11, 30),
            )
        })
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveDate(composeRule, day.toEpochDay())
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveDate(composeRule, day.toEpochDay())
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        composeRule.onNodeWithText("Persist question").assertIsDisplayed()
    }

    private fun setContent(
        started: Boolean = false,
        seedArchiveData: (suspend AcceleratedUiTestHarness.() -> Unit)? = null,
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
                )
            }
        }
        if (started || seedArchiveData != null) {
            PracticeComposeTestSupport.waitForHome(composeRule)
        }
    }

    private suspend fun AcceleratedUiTestHarness.seedAnswer(
        cyclePosition: Int,
        questionText: String,
        answerText: String,
        createdAt: Long,
    ) {
        val occurrenceId = database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
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
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
