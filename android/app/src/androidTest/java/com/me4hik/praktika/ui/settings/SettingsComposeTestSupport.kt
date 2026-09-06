// 06.08.2026 Settings Schedule cursor by Me4Hik START - shared Settings Compose test helpers
package com.me4hik.praktika.ui.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice

object SettingsComposeTestSupport {
    fun changeSlotTime(
        composeRule: ComposeContentTestRule,
        slotIndex: Int,
        timeOfDayMinutes: Int,
    ) {
        changeSlotTimeWithTags(
            composeRule = composeRule,
            slotTestTag = slotTestTag(slotIndex),
            pickerTestTag = SettingsTestTags.SETTINGS_TIME_PICKER,
            timeOfDayMinutes = timeOfDayMinutes,
        )
    }

    // 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - shared slot picker helper
    fun changeSlotTimeWithTags(
        composeRule: ComposeContentTestRule,
        slotTestTag: String,
        pickerTestTag: String,
        timeOfDayMinutes: Int,
    ) {
        val hour = timeOfDayMinutes / 60
        val minute = timeOfDayMinutes % 60
        val expectedText = formatTimeOfDayMinutes(timeOfDayMinutes)
        var lastError: Throwable? = null
        repeat(3) {
            try {
                composeRule.onNodeWithTag(slotTestTag)
                    .performScrollTo()
                    .assertIsDisplayed()
                    .performClick()
                composeRule.onNodeWithTag(pickerTestTag).assertIsDisplayed()
                composeRule.waitForIdle()
                Thread.sleep(300)
                selectHourAndMinute(composeRule, hour, minute)
                composeRule.onNodeWithText("Готово").performClick()
                waitUntilTimeDisplayed(composeRule, expectedText, timeoutMillis = 20_000)
                composeRule.waitForIdle()
                return
            } catch (error: Throwable) {
                lastError = error
                dismissTimePickerIfVisible(composeRule)
                composeRule.waitForIdle()
            }
        }
        throw lastError ?: AssertionError("Failed to change slot $slotTestTag to $expectedText")
    }
    // 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END

    fun saveSchedule(composeRule: ComposeContentTestRule) {
        waitForScheduleAutosaveIdle(composeRule)
    }

    fun waitForScheduleAutosaveIdle(
        composeRule: ComposeContentTestRule,
        timeoutMillis: Long = 15_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            !hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_SCHEDULE_PROGRESS)
        }
        composeRule.waitForIdle()
    }

    fun assertSaveScheduleButtonAbsent(composeRule: ComposeContentTestRule) {
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCHEDULE_SAVE).assertDoesNotExist()
    }

    fun cancelTimePicker(composeRule: ComposeContentTestRule) {
        composeRule.onNodeWithText("Отмена").performClick()
        composeRule.waitForIdle()
    }

    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - scroll-safe settings controls
    fun clickTaggedControl(
        composeRule: ComposeContentTestRule,
        tag: String,
    ) {
        composeRule.onNodeWithTag(tag)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
    }

    fun openNotificationsSettings(composeRule: ComposeContentTestRule) {
        clickTaggedControl(composeRule, SettingsTestTags.SETTINGS_NOTIFICATIONS_ENTRY)
        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATIONS_SETTINGS_SCREEN)
            .assertIsDisplayed()
    }

    fun waitUntilPauseProgressIdle(composeRule: ComposeContentTestRule) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !hasPauseProgress(composeRule)
        }
    }

    fun hasPauseProgress(composeRule: ComposeContentTestRule): Boolean {
        return hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_PAUSE_PROGRESS)
    }

    fun waitUntilPracticePauseUi(
        composeRule: ComposeContentTestRule,
        paused: Boolean,
        timeoutMillis: Long = 10_000,
    ) {
        val expectedLabel = practicePauseLabel(paused)
        composeRule.waitUntil(timeoutMillis) {
            try {
                composeRule.onNodeWithText(expectedLabel)
                    .performScrollTo()
                    .assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    fun clickPauseResumeControl(
        composeRule: ComposeContentTestRule,
        expectPaused: Boolean,
    ) {
        waitUntilPauseProgressIdle(composeRule)
        waitUntilPracticePauseUi(composeRule, paused = expectPaused)
        val expectedLabel = practicePauseLabel(expectPaused)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNode(
                    hasTestTag(SettingsTestTags.SETTINGS_PAUSE_RESUME) and hasText(expectedLabel),
                ).assertIsEnabled()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeRule.onNode(
            hasTestTag(SettingsTestTags.SETTINGS_PAUSE_RESUME) and hasText(expectedLabel),
        )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
    }

    private fun practicePauseLabel(paused: Boolean): String {
        return if (paused) {
            "Продолжить практику"
        } else {
            "Приостановить практику"
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END

    // 06.08.2026 Stage 10 Blackview E2E cursor by Me4Hik START - wait for draft time text
    fun waitUntilTimeDisplayed(
        composeRule: ComposeContentTestRule,
        timeText: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            hasNodeWithText(composeRule, timeText)
        }
    }

    private fun hasNodeWithText(
        composeRule: ComposeContentTestRule,
        text: String,
    ): Boolean {
        return try {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun hasNodeWithTag(
        composeRule: ComposeContentTestRule,
        tag: String,
    ): Boolean {
        return try {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false
        }
    }
    // 06.08.2026 Stage 10 Blackview E2E cursor by Me4Hik END

    fun waitForSnackbarText(
        composeRule: ComposeContentTestRule,
        text: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            hasNodeWithText(composeRule, text)
        }
    }

    fun waitForSnackbarDismiss(
        composeRule: ComposeContentTestRule,
        text: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            !hasNodeWithText(composeRule, text)
        }
        composeRule.waitForIdle()
    }

    private fun selectHourAndMinute(
        composeRule: ComposeContentTestRule,
        hour: Int,
        minute: Int,
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        composeRule.waitForIdle()
        switchToTextInputMode(composeRule, device)
        if (tryFillTextInput(composeRule, device, hour, minute)) {
            return
        }

        clickHourValue(composeRule, hour)
        composeRule.waitForIdle()
        Thread.sleep(400)
        clickMinuteValue(composeRule, minute)
        composeRule.waitForIdle()
    }

    private fun switchToTextInputMode(
        composeRule: ComposeContentTestRule,
        device: UiDevice,
    ) {
        listOf(
            "Switch to text input mode",
            "Переключиться в режим ввода текста",
            "Text input mode",
        ).forEach { label ->
            try {
                composeRule.onNode(
                    hasContentDescription(label, substring = true) and hasClickAction(),
                    useUnmergedTree = true,
                ).performClick()
                return
            } catch (_: AssertionError) {
            } catch (_: IllegalStateException) {
            }
            device.findObject(By.descContains(label))?.click()
        }
    }

    private fun tryFillTextInput(
        composeRule: ComposeContentTestRule,
        device: UiDevice,
        hour: Int,
        minute: Int,
    ): Boolean {
        val composeFields = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
        if (composeFields.size >= 2) {
            val hourText = hour.toString().padStart(2, '0')
            val minuteText = minute.toString().padStart(2, '0')
            composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0]
                .performTextReplacement(hourText)
            composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1]
                .performTextReplacement(minuteText)
            return true
        }

        val fields = device.findObjects(By.clazz("android.widget.EditText"))
        if (fields.size < 2) {
            return false
        }
        val hourText = hour.toString().padStart(2, '0')
        val minuteText = minute.toString().padStart(2, '0')
        fields[0].click()
        fields[0].text = hourText
        fields[1].click()
        fields[1].text = minuteText
        return true
    }

    private fun clickHourValue(
        composeRule: ComposeContentTestRule,
        hour: Int,
    ) {
        clickTraversalRadioButton(composeRule, hour.toFloat())
    }

    private fun clickMinuteValue(
        composeRule: ComposeContentTestRule,
        minute: Int,
    ) {
        composeRule.waitForIdle()
        Thread.sleep(400)
        try {
            composeRule.onNode(
                hasContentDescription("Выберите минуты", substring = true) and hasClickAction(),
                useUnmergedTree = true,
            ).performClick()
        } catch (_: AssertionError) {
        }
        composeRule.waitForIdle()
        Thread.sleep(400)
        val minuteIndex = (minute / 5).toFloat()
        clickTraversalRadioButton(composeRule, minuteIndex)
    }

    private fun clickTraversalRadioButton(
        composeRule: ComposeContentTestRule,
        traversalIndex: Float,
    ) {
        val matcher = SemanticsMatcher("TraversalIndex=$traversalIndex") { node ->
            val index = node.config.getOrNull(SemanticsProperties.TraversalIndex)
            index != null && kotlin.math.abs(index - traversalIndex) < 0.001f
        }
        composeRule.onAllNodes(matcher, useUnmergedTree = true).apply {
            fetchSemanticsNodes(atLeastOneRootRequired = false).firstOrNull()
                ?: throw AssertionError("Node with TraversalIndex=$traversalIndex not found")
        }[0].performClick()
    }

    private fun dismissTimePickerIfVisible(composeRule: ComposeContentTestRule) {
        try {
            composeRule.onNodeWithText("Отмена").performClick()
        } catch (_: AssertionError) {
        } catch (_: IllegalStateException) {
        }
    }

    private fun slotTestTag(slotIndex: Int): String {
        return when (slotIndex) {
            1 -> SettingsTestTags.SETTINGS_SLOT_1
            2 -> SettingsTestTags.SETTINGS_SLOT_2
            3 -> SettingsTestTags.SETTINGS_SLOT_3
            else -> SettingsTestTags.SETTINGS_SLOT_1
        }
    }
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END
