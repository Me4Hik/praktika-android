// 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik START - production read-only journey
package com.me4hik.praktika.stage23

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.archive.ArchiveComposeTestSupport
import com.me4hik.praktika.ui.archive.ArchiveTestTags
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsComposeTestSupport
import com.me4hik.praktika.ui.settings.SettingsTestTags
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage23ProductionIntegratedJourneyInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val zone = ZoneId.of("Europe/Kiev")

    @Before
    fun assumeOptIn() {
        assumeTrue(
            "Stage 23 runs only via stage23_functional_acceptance.ps1",
            InstrumentationRegistry.getArguments().getString(OPT_IN_ARGUMENT).toBoolean(),
        )
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun productionReadOnlyIntegratedJourney() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val journeyTargets = runBlocking {
            val runtime = PraktikaRuntimeHolder.get(context).also { it.initializer.ensureInitialized() }

            val practice = runtime.database.practiceStateDao().get()!!
            assertTrue(practice.isPracticeStarted)
            assertFalse(practice.isPaused)

            val answers = runtime.database.answerDao().getAllOrderedByCreatedAt()
            assertEquals(Stage23ProductionExpectedFixture.EXPECTED_ANSWERS_COUNT, answers.size)

            JourneyTargets(
                answerOneId = answers.first().id,
                questionThreeId = Stage23ProductionExpectedFixture.FIXTURE_QUESTION_ID_THREE,
                epochDay = Instant.ofEpochMilli(answers.first().createdAtEpochMillis)
                    .atZone(zone)
                    .toLocalDate()
                    .toEpochDay(),
            )
        }

        composeRule.waitUntil(timeoutMillis = 25_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION) ||
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ARCHIVE)
        }
        assertFalse(
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START),
        )
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()

        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)

        ArchiveComposeTestSupport.clickArchiveDate(composeRule, journeyTargets.epochDay)
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        composeRule.onNodeWithTag(ArchiveTestTags.DAY_LIST).assertIsDisplayed()

        composeRule.onNodeWithTag("${ArchiveTestTags.SHARE_BUTTON_PREFIX}${journeyTargets.answerOneId}")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.ENTRY_SHARE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.ENTRY_SHARE_CANCEL).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.ENTRY_SHARE_DIALOG)
        }
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)

        ArchiveComposeTestSupport.clickDeleteButton(composeRule, journeyTargets.answerOneId)
        ArchiveComposeTestSupport.waitForDeleteDialog(composeRule)
        ArchiveComposeTestSupport.clickDeleteCancel(composeRule)

        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)

        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, journeyTargets.questionThreeId)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST).assertIsDisplayed()
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)

        PracticeComposeTestSupport.assertHomePositionDisplayed(composeRule)

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("10:30").assertIsDisplayed()
        composeRule.onNodeWithText("14:20").assertIsDisplayed()
        composeRule.onNodeWithText("20:10").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SOUND_SWITCH).assertDoesNotExist()
        SettingsComposeTestSupport.openNotificationsSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SOUND_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATIONS_SETTINGS_BACK)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        PracticeComposeTestSupport.clickSettingsBackToHome(composeRule)

        android.util.Log.i(TAG, "STAGE23_PRODUCTION_JOURNEY_GREEN")
    }

    private data class JourneyTargets(
        val answerOneId: Long,
        val questionThreeId: Int,
        val epochDay: Long,
    )

    private companion object {
        const val OPT_IN_ARGUMENT = "stage23_functional_acceptance"
        const val TAG = "Stage23ProductionJourney"
    }
}
// 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik END
