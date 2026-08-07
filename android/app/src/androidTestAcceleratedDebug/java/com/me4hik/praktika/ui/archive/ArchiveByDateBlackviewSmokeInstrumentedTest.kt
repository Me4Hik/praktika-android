// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - Blackview visual smoke
package com.me4hik.praktika.ui.archive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveByDateBlackviewSmokeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val zone = ZoneId.of("Europe/Kiev")

    @Before
    fun assumeOptIn() {
        assumeTrue(
            "stage15_archive_smoke opt-in required",
            InstrumentationRegistry.getArguments().getString("stage15_archive_smoke") == "true",
        )
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun archiveByDateSmokeTwoDays() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = PraktikaRuntimeHolder.get(context)
        val practiceState = runtime.database.practiceStateDao().get()
        if (practiceState?.isPracticeStarted != true) {
            runtime.cycleRepository.startPractice()
        }
        val dayOne = LocalDate.of(2026, 8, 5)
        val dayTwo = LocalDate.of(2026, 8, 7)
        val nextPosition = runtime.database.questionOccurrenceDao()
            .getAllOrderedByPlannedAt()
            .maxOfOrNull { it.cyclePosition }
            ?.plus(1)
            ?: 2
        val smokeMinute = 40 + (nextPosition % 10)
        val smokeQuestionTwo = "Smoke question 2 #$nextPosition"
        val smokeAnswerTwo = "Smoke multiline $nextPosition\nВторая строка smoke"
        seedAnswer(runtime, nextPosition, "Smoke question 1", "Smoke answer 1", epochMillis(dayOne, 9, 0))
        seedAnswer(
            runtime,
            nextPosition + 1,
            smokeQuestionTwo,
            smokeAnswerTwo,
            epochMillis(dayTwo, 18, smokeMinute),
        )

        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ARCHIVE) ||
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
        }

        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesScreen(composeRule)
        composeRule.onNodeWithTag("${ArchiveTestTags.DATE_ITEM_PREFIX}${dayTwo.toEpochDay()}").assertIsDisplayed()
        composeRule.onNodeWithTag("${ArchiveTestTags.DATE_ITEM_PREFIX}${dayOne.toEpochDay()}").assertIsDisplayed()
        saveArtifact("archive_dates.png")

        ArchiveComposeTestSupport.clickArchiveDate(composeRule, dayTwo.toEpochDay())
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        composeRule.onNodeWithText(smokeQuestionTwo).assertIsDisplayed()
        composeRule.onNodeWithText(smokeAnswerTwo).assertIsDisplayed()
        val smokeTimeLabel = "%02d:%02d".format(18, smokeMinute)
        composeRule.onNodeWithTag(ArchiveTestTags.DAY_LIST)
            .performScrollToNode(hasText(smokeTimeLabel, substring = true))
        composeRule.onNodeWithText(smokeTimeLabel, substring = true).assertIsDisplayed()
        saveArtifact("archive_day.png")

        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesScreen(composeRule)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.assertHomePositionDisplayed(composeRule)
    }

    private suspend fun seedAnswer(
        runtime: com.me4hik.praktika.runtime.PraktikaRuntime,
        cyclePosition: Int,
        questionText: String,
        answerText: String,
        createdAt: Long,
    ) {
        val occurrenceId = runtime.database.questionOccurrenceDao().insert(
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
        runtime.database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = answerText,
                createdAtEpochMillis = createdAt,
            ),
        )
    }

    private fun saveArtifact(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val dir = File("/sdcard/Download/stage15_archive_smoke")
        dir.mkdirs()
        device.takeScreenshot(File(dir, name))
    }

    private fun epochMillis(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
