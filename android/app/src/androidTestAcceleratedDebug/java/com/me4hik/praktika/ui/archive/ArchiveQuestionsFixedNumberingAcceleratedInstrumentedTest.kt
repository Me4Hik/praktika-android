// 03.10.2026 Archive fixed numbering cursor by Me4Hik START - UI acceptance cyclePosition order/leading
package com.me4hik.praktika.ui.archive

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveQuestionsFixedNumberingAcceleratedInstrumentedTest {
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
    fun fixedNumbering_orderAndLeading_stableAfterNewerActivityOnQ15() {
        val t3 = epochMillis(LocalDate.of(2026, 8, 5), 10, 0)
        val t8 = epochMillis(LocalDate.of(2026, 8, 6), 12, 0)
        val t15Older = epochMillis(LocalDate.of(2026, 8, 7), 14, 0)
        val t15Newer = epochMillis(LocalDate.of(2026, 8, 9), 18, 0)

        setContent(seedArchiveData = {
            // questionId == cyclePosition for all fixtures (do not reuse mismatched seeds).
            seedQuestionAnswer(
                questionId = 3,
                cycleNumber = 1,
                cyclePosition = 3,
                questionText = "Fixed numbering alpha",
                answerText = "Answer for alpha",
                createdAt = t3,
            )
            seedQuestionAnswer(
                questionId = 8,
                cycleNumber = 1,
                cyclePosition = 8,
                questionText = "Fixed numbering bravo",
                answerText = "Answer for bravo",
                createdAt = t8,
            )
            seedQuestionAnswer(
                questionId = 15,
                cycleNumber = 1,
                cyclePosition = 15,
                questionText = "Fixed numbering charlie",
                answerText = "Answer for charlie older",
                createdAt = t15Older,
            )
        })

        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTIONS_LIST).assertIsDisplayed()

        assertFixedNumberingListContract()

        // Newer terminal activity on highest cyclePosition must not lift the row.
        runBlocking {
            harness.seedQuestionAnswer(
                questionId = 15,
                cycleNumber = 2,
                cyclePosition = 15,
                questionText = "Fixed numbering charlie",
                answerText = "Answer for charlie newer",
                createdAt = t15Newer,
            )
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(
                    composeRule = composeRule,
                    questionId = 15,
                    text = "Ответов: 2",
                    substring = false,
                )
                true
            }.getOrDefault(false)
        }

        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        ArchiveComposeTestSupport.waitForArchiveHubScreen(composeRule)
        ArchiveComposeTestSupport.openArchiveQuestions(composeRule)
        ArchiveComposeTestSupport.waitForArchiveQuestionsContent(composeRule)

        assertFixedNumberingListContract()
        ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(
            composeRule = composeRule,
            questionId = 15,
            text = "Ответов: 2",
            substring = false,
        )
    }

    private fun assertFixedNumberingListContract() {
        val order = ArchiveComposeTestSupport.archiveQuestionItemOrder(composeRule)
        assertEquals(listOf(3, 8, 15), order)
        assertNotEquals(
            "Leading/order must not collapse to list ranks 1,2,3",
            listOf(1, 2, 3),
            order,
        )

        ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(
            composeRule,
            questionId = 3,
            text = "Fixed numbering alpha",
        )
        ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(
            composeRule,
            questionId = 8,
            text = "Fixed numbering bravo",
        )
        ArchiveComposeTestSupport.assertArchiveQuestionItemContainsText(
            composeRule,
            questionId = 15,
            text = "Fixed numbering charlie",
        )

        ArchiveComposeTestSupport.assertArchiveQuestionLeadingNumber(composeRule, questionId = 3, expectedLeading = 3)
        ArchiveComposeTestSupport.assertArchiveQuestionLeadingNumber(composeRule, questionId = 8, expectedLeading = 8)
        ArchiveComposeTestSupport.assertArchiveQuestionLeadingNumber(composeRule, questionId = 15, expectedLeading = 15)

        // Row ranks under activity-sort would be 1/2/3; prove those bare tokens are absent as leading
        // for Q8/Q15 (Q3 row may contain "Ответов: 1", so only check higher rows for "1"/"2").
        assertTrue(
            "Q8 leading must be 8, not row rank 2",
            rowContainsExactText(questionId = 8, text = "8"),
        )
        assertTrue(
            "Q15 leading must be 15, not row rank 3",
            rowContainsExactText(questionId = 15, text = "15"),
        )
        assertTrue(!rowContainsExactText(questionId = 8, text = "2"))
        assertTrue(!rowContainsExactText(questionId = 15, text = "3"))
    }

    private fun rowContainsExactText(questionId: Int, text: String): Boolean {
        val tag = "${ArchiveTestTags.QUESTION_ITEM_PREFIX}$questionId"
        return runCatching {
            composeRule.onNodeWithTag(tag)
                .assert(hasAnyDescendant(hasText(text, substring = false)))
            true
        }.getOrDefault(false)
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
        require(questionId == cyclePosition) {
            "fixed-numbering fixture requires questionId == cyclePosition, got $questionId / $cyclePosition"
        }
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
// 03.10.2026 Archive fixed numbering cursor by Me4Hik END
