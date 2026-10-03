// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - Blackview smoke delete Answer
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
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage18DeleteAnswerBlackviewSmokeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val zone = ZoneId.of("Europe/Kiev")

    @Before
    fun assumeOptIn() {
        assumeTrue(
            "stage18_delete_smoke opt-in required",
            InstrumentationRegistry.getArguments().getString("stage18_delete_smoke") == "true",
        )
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun deleteRepeatAnswerFromQuestionHistorySmoke() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = PraktikaRuntimeHolder.get(context)
        val practiceStateBefore = runtime.database.practiceStateDao().get()!!
        if (!practiceStateBefore.isPracticeStarted) {
            runtime.cycleRepository.startPractice()
        }
        runtime.archiveReadRepository.observeEntriesForQuestion(1).first()
            .forEach { existing -> runtime.answerDeleteRepository.deleteAnswer(existing.answerId) }
        val nextPosition = runtime.database.questionOccurrenceDao()
            .getAllOrderedByPlannedAt()
            .maxOfOrNull { it.cyclePosition }
            ?.plus(1)
            ?: 2
        val dayOne = LocalDate.of(2026, 8, 5)
        val dayTwo = LocalDate.of(2026, 8, 7)
        val answerOneText = "Stage18 smoke answer 1"
        val answerTwoText = "Stage18 smoke answer 2"
        val (_, answerOneId) = seedQuestionAnswer(
            runtime = runtime,
            questionId = 1,
            cycleNumber = 1,
            cyclePosition = nextPosition,
            questionText = "Stage18 smoke cycle 1",
            answerText = answerOneText,
            createdAt = epochMillis(dayOne, 9, 0),
        )
        val (occurrenceTwoId, answerTwoId) = seedQuestionAnswer(
            runtime = runtime,
            questionId = 1,
            cycleNumber = 2,
            cyclePosition = nextPosition + 1,
            questionText = "Stage18 smoke cycle 2",
            answerText = answerTwoText,
            createdAt = epochMillis(dayTwo, 18, 30),
        )
        val (_, answerThreeId) = seedQuestionAnswer(
            runtime = runtime,
            questionId = 2,
            cycleNumber = 1,
            cyclePosition = nextPosition + 2,
            questionText = "Stage18 smoke question B",
            answerText = "Stage18 smoke answer B",
            createdAt = epochMillis(dayTwo, 12, 0),
        )

        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ARCHIVE) ||
                PracticeComposeTestSupport.hasHomeStartedContent(composeRule)
        }

        val seededHistory = runtime.archiveReadRepository.observeEntriesForQuestion(1).first()
        check(seededHistory.any { it.answerId == answerOneId })
        check(seededHistory.any { it.answerId == answerTwoId })

        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        ArchiveComposeTestSupport.clickArchiveQuestion(composeRule, questionId = 1)
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        val answerOneEntryTag = "${ArchiveTestTags.QUESTION_HISTORY_ENTRY_PREFIX}$answerOneId"
        val answerTwoEntryTag = "${ArchiveTestTags.QUESTION_HISTORY_ENTRY_PREFIX}$answerTwoId"
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
                    .performScrollToNode(hasText(answerOneText))
                composeRule.onNodeWithTag(answerOneEntryTag).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText(answerTwoText))
        composeRule.onNodeWithTag(answerTwoEntryTag).assertIsDisplayed()
        ArchiveComposeTestSupport.clickDeleteButton(composeRule, answerTwoId)
        ArchiveComposeTestSupport.waitForDeleteDialog(composeRule)
        ArchiveComposeTestSupport.confirmDelete(composeRule, answerTwoId)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(answerTwoEntryTag).assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText(answerOneText))
        composeRule.onNodeWithTag(answerOneEntryTag).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_SCREEN).assertIsDisplayed()

        val db = runtime.database.openHelper.writableDatabase
        assertEquals("ok", db.query("PRAGMA quick_check").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        })
        assertEquals(2, db.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        })

        assertNull(runtime.database.answerDao().getById(answerTwoId))
        assertEquals(answerOneText, runtime.database.answerDao().getById(answerOneId)!!.text)
        assertEquals("Stage18 smoke answer B", runtime.database.answerDao().getById(answerThreeId)!!.text)
        val occurrenceTwo = runtime.database.questionOccurrenceDao().getById(occurrenceTwoId)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, occurrenceTwo.status)
        assertNull(runtime.database.answerDao().getByOccurrenceId(occurrenceTwoId))

        val practiceStateAfter = runtime.database.practiceStateDao().get()!!
        assertEquals(practiceStateBefore.nextCyclePosition, practiceStateAfter.nextCyclePosition)
        assertEquals(practiceStateBefore.currentCycleNumber, practiceStateAfter.currentCycleNumber)

        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        composeRule.onNodeWithTag("${ArchiveTestTags.QUESTION_ITEM_PREFIX}1").assertIsDisplayed()
        ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(composeRule, questionId = 1, "Ответов: 1")
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
    ): Pair<Long, Long> {
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
        val answerId = runtime.database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = answerText,
                createdAtEpochMillis = createdAt,
            ),
        )
        return occurrenceId to answerId
    }

    private fun epochMillis(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
