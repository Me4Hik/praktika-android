// 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik START - integrated navigation
package com.me4hik.praktika.stage23

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.ui.archive.ArchiveComposeTestSupport
import com.me4hik.praktika.ui.archive.ArchiveTestTags
import com.me4hik.praktika.ui.practice.AcceleratedUiTestHarness
import com.me4hik.praktika.ui.practice.PracticeComposeTestActivity
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeRootViewModel
import com.me4hik.praktika.ui.practice.PracticeRootViewModelFactory
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsTestTags
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage23IntegratedNavigationAcceleratedInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PracticeComposeTestActivity>()

    private lateinit var harness: AcceleratedUiTestHarness
    private val zone = ZoneId.of("Europe/Kiev")
    private var seededAnswerIdOne: Long = 0L
    private var seededDayEpoch: Long = 0L

    @Before
    fun assumeOptIn() {
        assumeTrue(
            "Stage 23 runs only via stage23_functional_acceptance.ps1",
            InstrumentationRegistry.getArguments().getString(OPT_IN_ARGUMENT).toBoolean(),
        )
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) {
            harness.tearDown()
        }
    }

    @Test
    fun integratedNavigationArchiveExportShareSettings() {
        val dayOne = LocalDate.of(2026, 8, 5)
        val dayTwo = LocalDate.of(2026, 8, 7)
        seededDayEpoch = dayOne.toEpochDay()
        setContent(seedArchiveData = {
            seededAnswerIdOne = seedAnswer(
                questionId = 1,
                cyclePosition = 2,
                questionText = "Stage23 nav question one",
                answerText = "Stage23 nav answer one",
                createdAt = epochMillis(dayOne, 10, 0),
            )
            seedAnswer(
                questionId = 3,
                cyclePosition = 4,
                questionText = "Stage23 nav question three",
                answerText = "Stage23 nav answer three",
                createdAt = epochMillis(dayTwo, 14, 30),
            )
        })

        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()

        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)

        ArchiveComposeTestSupport.clickArchiveDate(composeRule, seededDayEpoch)
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)

        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, questionId = 3)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)

        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_CANCEL).performClick()

        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_FORMAT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_CANCEL).performClick()

        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_CANCEL).performClick()

        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_PERIOD).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_PERIOD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_CANCEL).performClick()

        ArchiveComposeTestSupport.clickArchiveDate(composeRule, seededDayEpoch)
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        composeRule.onNodeWithTag("${ArchiveTestTags.SHARE_BUTTON_PREFIX}$seededAnswerIdOne")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.ENTRY_SHARE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.ENTRY_SHARE_CANCEL).performClick()
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.waitForHome(composeRule)

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        PracticeComposeTestSupport.clickSettingsBackToHome(composeRule)
    }

    private fun setContent(seedArchiveData: suspend AcceleratedUiTestHarness.() -> Unit) {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        harness = AcceleratedUiTestHarness(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking {
            harness.setUp(initialHour = 11, initialMinute = 0)
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
                AppNavigation(runtime = harness.runtime, viewModel = rootViewModel)
            }
        }
    }

    private suspend fun AcceleratedUiTestHarness.seedAnswer(
        questionId: Int,
        cyclePosition: Int,
        questionText: String,
        answerText: String,
        createdAt: Long,
    ): Long {
        val occurrenceId = database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = questionText,
                cycleNumber = 1,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = createdAt - 3_600_000,
                availableUntilEpochMillis = createdAt + 3_600_000,
                completedAtEpochMillis = createdAt,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = zone.id,
            ),
        )
        return database.answerDao().insert(
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

    private companion object {
        const val OPT_IN_ARGUMENT = "stage23_functional_acceptance"
    }
}
// 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik END
