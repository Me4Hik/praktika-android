// 07.08.2026 Stage 21 Share cursor by Me4Hik START - Blackview real chooser smoke
package com.me4hik.praktika.ui.archive

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.share.ShareIntentFactory
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage21ShareBlackviewSmokeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val zone = ZoneId.of("Europe/Moscow")

    @Before
    fun assumeOptIn() {
        assumeTrue(
            "stage21_share_smoke opt-in required",
            InstrumentationRegistry.getArguments().getString("stage21_share_smoke") == "true",
        )
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun textShareOpensAndroidChooser() {
        val answerId = runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            prepareSingleEntryRuntime(context)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val appPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ARCHIVE) ||
                PracticeComposeTestSupport.hasHomeStartedContent(composeRule)
        }
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesContent(composeRule)
        val day = LocalDate.of(2026, 8, 7)
        ArchiveComposeTestSupport.clickArchiveDate(composeRule, day.toEpochDay())
        ArchiveComposeTestSupport.waitForArchiveDayContent(composeRule)
        composeRule.onNodeWithTag("${ArchiveTestTags.SHARE_BUTTON_PREFIX}$answerId")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.ENTRY_SHARE_QUESTION_AND_ANSWER).performClick()
        waitForExternalChooser(device, appPackage, timeoutMs = 20_000)
        device.pressBack()
    }

    @Test
    fun fileShareOpensChooserWithReadableContentUri() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            prepareSingleEntryRuntime(context)
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val appPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ARCHIVE) ||
                PracticeComposeTestSupport.hasHomeStartedContent(composeRule)
        }
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.expandArchiveExportShare(composeRule)
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_MARKDOWN).performClick()
        waitForExternalChooser(device, appPackage, timeoutMs = 20_000)
        device.pressBack()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            device.currentPackageName == appPackage
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val shareRoot = File(context.cacheDir, "share")
        val latestFile = shareRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".md") }
            .maxByOrNull { it.lastModified() }
        assertTrue(latestFile != null)
        assertTrue(latestFile!!.length() > 0L)
        val authority = ShareIntentFactory.fileProviderAuthority(context.packageName)
        val uri = FileProvider.getUriForFile(context, authority, latestFile)
        assertTrue(uri.scheme == "content")
        context.contentResolver.openInputStream(uri).use { input ->
            assertTrue(input!!.readBytes().isNotEmpty())
        }
    }

    private fun waitForExternalChooser(device: UiDevice, appPackage: String, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val currentPackage = device.currentPackageName
            if (!currentPackage.isNullOrBlank() && currentPackage != appPackage) {
                return
            }
            Thread.sleep(500)
        }
        org.junit.Assert.fail("Expected Android share chooser to open within ${timeoutMs}ms")
    }

    private suspend fun prepareSingleEntryRuntime(
        context: Context,
    ): Long {
        val runtime = PraktikaRuntimeHolder.get(context)
        val practiceState = runtime.database.practiceStateDao().get()!!
        if (!practiceState.isPracticeStarted) {
            runtime.cycleRepository.startPractice()
        }
        runtime.archiveReadRepository.observeEntries().first()
            .forEach { runtime.answerDeleteRepository.deleteAnswer(it.answerId) }
        val nextPosition = runtime.database.questionOccurrenceDao()
            .getAllOrderedByPlannedAt()
            .maxOfOrNull { it.cyclePosition }
            ?.plus(1)
            ?: 100
        val day = LocalDate.of(2026, 8, 7)
        val occurrenceId = runtime.database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Smoke share snapshot question",
                cycleNumber = nextPosition,
                cyclePosition = nextPosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochMillis(day, 11, 0) - 3_600_000,
                availableUntilEpochMillis = epochMillis(day, 11, 0) + 3_600_000,
                status = QuestionOccurrenceStatus.ANSWERED,
                completedAtEpochMillis = epochMillis(day, 11, 0),
                zoneId = zone.id,
            ),
        )
        val answerId = runtime.database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Smoke share answer",
                createdAtEpochMillis = epochMillis(day, 11, 0),
            ),
        )
        assertEquals(1, runtime.archiveReadRepository.observeEntries().first().size)
        return answerId
    }

    private fun epochMillis(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
