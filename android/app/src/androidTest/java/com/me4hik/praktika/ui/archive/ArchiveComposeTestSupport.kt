// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - Compose helpers архива
package com.me4hik.praktika.ui.archive

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport

object ArchiveComposeTestSupport {
    fun openArchiveFromHome(composeRule: ComposeContentTestRule) {
        PracticeComposeTestSupport.clickHomeArchive(composeRule)
    }

    // PROMPT 167 — archive root is hub; date list lives on archive/days
    fun waitForArchiveHubScreen(composeRule: ComposeContentTestRule) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.HUB_SCREEN).assertExists()
                true
            }.getOrDefault(false)
        }
    }

    fun openArchiveDaysFromHub(composeRule: ComposeContentTestRule) {
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_DAYS)
            .assertIsDisplayed()
            .performClick()
    }

    // PROMPT 170 — expand export/share accordion before clicking nested actions
    fun expandArchiveExportShare(composeRule: ComposeContentTestRule) {
        if (PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.EXPORT_ALL)) {
            return
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_SHARE_ACCORDION)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.EXPORT_ALL)
        }
    }

    fun ensureArchiveHubScreen(composeRule: ComposeContentTestRule) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.HUB_SCREEN) ||
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.DATES_SCREEN)
        }
        if (
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.DATES_SCREEN) &&
            !PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.HUB_SCREEN)
        ) {
            clickArchiveBack(composeRule)
        }
        waitForArchiveHubScreen(composeRule)
    }

    fun waitForArchiveDatesScreen(composeRule: ComposeContentTestRule) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.DATES_SCREEN).assertExists()
                true
            }.getOrDefault(false)
        }
    }

    fun waitForArchiveDatesContent(composeRule: ComposeContentTestRule) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.HUB_SCREEN) ||
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.DATES_SCREEN)
        }
        if (!PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.DATES_SCREEN)) {
            waitForArchiveHubScreen(composeRule)
            openArchiveDaysFromHub(composeRule)
        }
        waitForArchiveDatesScreen(composeRule)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.DATES_LIST).assertExists()
                true
            }.getOrElse {
                runCatching {
                    composeRule.onNodeWithTag(ArchiveTestTags.DATES_EMPTY).assertExists()
                    true
                }.getOrDefault(false)
            }
        }
    }

    fun clickArchiveBack(composeRule: ComposeContentTestRule) {
        composeRule.onNodeWithTag(ArchiveTestTags.BACK).assertIsDisplayed().performClick()
    }

    fun clickArchiveDate(composeRule: ComposeContentTestRule, epochDay: Long) {
        val tag = "${ArchiveTestTags.DATE_ITEM_PREFIX}$epochDay"
        composeRule.onNodeWithTag(ArchiveTestTags.DATES_LIST)
            .performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .performClick()
    }

    fun waitForArchiveDayScreen(composeRule: ComposeContentTestRule) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.DAY_SCREEN).assertExists()
                true
            }.getOrDefault(false)
        }
    }

    fun waitForArchiveDayContent(composeRule: ComposeContentTestRule) {
        waitForArchiveDayScreen(composeRule)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.DAY_LIST).assertExists()
                true
            }.getOrElse {
                runCatching {
                    composeRule.onNodeWithText("За этот день ответов нет").assertExists()
                    true
                }.getOrDefault(false)
            }
        }
    }

    fun assertArchiveEmpty(composeRule: ComposeContentTestRule) {
        composeRule.onNodeWithTag(ArchiveTestTags.DATES_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Архив пока пуст").assertIsDisplayed()
    }

    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - helpers архива по вопросам
    fun openArchiveQuestions(composeRule: ComposeContentTestRule) {
        ensureArchiveHubScreen(composeRule)
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_QUESTIONS)
            .assertIsDisplayed()
            .performClick()
    }

    fun waitForArchiveQuestionsScreen(composeRule: ComposeContentTestRule) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.QUESTIONS_SCREEN).assertExists()
                true
            }.getOrDefault(false)
        }
    }

    fun waitForArchiveQuestionsContent(composeRule: ComposeContentTestRule) {
        waitForArchiveQuestionsScreen(composeRule)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.QUESTIONS_LIST).assertExists()
                true
            }.getOrElse {
                runCatching {
                    composeRule.onNodeWithTag(ArchiveTestTags.QUESTIONS_EMPTY).assertExists()
                    true
                }.getOrDefault(false)
            }
        }
    }

    fun clickArchiveQuestion(composeRule: ComposeContentTestRule, questionId: Int) {
        val tag = "${ArchiveTestTags.QUESTION_ITEM_PREFIX}$questionId"
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTIONS_LIST)
            .performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .performClick()
    }

    fun assertArchiveQuestionItemContainsText(
        composeRule: ComposeContentTestRule,
        questionId: Int,
        text: String,
        substring: Boolean = false,
    ) {
        val tag = "${ArchiveTestTags.QUESTION_ITEM_PREFIX}$questionId"
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTIONS_LIST)
            .performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag)
            .assert(hasAnyDescendant(hasText(text, substring = substring)))
            .assertIsDisplayed()
    }

    // 03.10.2026 Archive fixed numbering cursor by Me4Hik START - order + leading helpers
    fun archiveQuestionItemOrder(composeRule: ComposeContentTestRule): List<Int> {
        val prefix = ArchiveTestTags.QUESTION_ITEM_PREFIX
        return composeRule
            .onNodeWithTag(ArchiveTestTags.QUESTIONS_LIST)
            .onChildren()
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: return@mapNotNull null
                if (!tag.startsWith(prefix)) {
                    return@mapNotNull null
                }
                tag.removePrefix(prefix).toIntOrNull()
            }
    }

    fun assertArchiveQuestionLeadingNumber(
        composeRule: ComposeContentTestRule,
        questionId: Int,
        expectedLeading: Int,
    ) {
        // Leading is the only exact digit token when title has no bare numbers and
        // supporting uses "Ответов: N" (not equal to cyclePosition for this fixture).
        assertArchiveQuestionItemContainsText(
            composeRule = composeRule,
            questionId = questionId,
            text = expectedLeading.toString(),
            substring = false,
        )
    }
    // 03.10.2026 Archive fixed numbering cursor by Me4Hik END

    fun waitForArchiveQuestionHistoryScreen(composeRule: ComposeContentTestRule) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_SCREEN).assertExists()
                true
            }.getOrDefault(false)
        }
    }

    fun waitForArchiveQuestionHistoryContent(composeRule: ComposeContentTestRule) {
        waitForArchiveQuestionHistoryScreen(composeRule)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST).assertExists()
                true
            }.getOrDefault(false)
        }
    }

    // 07.08.2026 Stage 25 Functional UI Fix cursor by Me4Hik START - scroll-safe history assertions
    fun assertQuestionHistoryTextDisplayed(
        composeRule: ComposeContentTestRule,
        text: String,
        substring: Boolean = false,
    ) {
        composeRule.onNodeWithTag(ArchiveTestTags.QUESTION_HISTORY_LIST)
            .performScrollToNode(hasText(text, substring = substring))
        if (substring) {
            composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
        } else {
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
    }

    fun assertQuestionHistoryCycleLabelDisplayed(
        composeRule: ComposeContentTestRule,
        cycleNumber: Int,
    ) {
        assertQuestionHistoryTextDisplayed(composeRule, "Цикл $cycleNumber")
    }
    // 07.08.2026 Stage 25 Functional UI Fix cursor by Me4Hik END
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END

    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - helpers delete confirmation
    fun clickDeleteButton(composeRule: ComposeContentTestRule, answerId: Long) {
        val deleteTag = "${ArchiveTestTags.DELETE_BUTTON_PREFIX}$answerId"
        val listTag = if (
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, ArchiveTestTags.DAY_LIST)
        ) {
            ArchiveTestTags.DAY_LIST
        } else {
            ArchiveTestTags.QUESTION_HISTORY_LIST
        }
        composeRule.onNodeWithTag(listTag)
            .performScrollToNode(hasTestTag(deleteTag))
        composeRule.onNodeWithTag(deleteTag)
            .assertIsDisplayed()
            .performClick()
    }

    fun waitForDeleteDialog(composeRule: ComposeContentTestRule) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ArchiveTestTags.DELETE_DIALOG).assertExists()
                true
            }.getOrDefault(false)
        }
    }

    fun clickDeleteCancel(composeRule: ComposeContentTestRule) {
        composeRule.onNodeWithTag(ArchiveTestTags.DELETE_CANCEL).assertIsDisplayed().performClick()
    }

    fun confirmDelete(composeRule: ComposeContentTestRule, answerId: Long) {
        composeRule.onNodeWithTag("${ArchiveTestTags.DELETE_CONFIRM_PREFIX}$answerId")
            .assertIsDisplayed()
            .performClick()
    }
    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
