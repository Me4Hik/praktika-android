package com.me4hik.praktika.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.archive.ArchiveHubScreen
import com.me4hik.praktika.ui.archive.ArchiveTestTags
import com.me4hik.praktika.ui.settings.SettingsTestTags
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Point proofs that SHOW_ONLY tour guards keep action callbacks at 0 while tour is active,
 * and restore normal behavior after Exit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourShowOnlySafetyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeStore : TesterToolsStore {
        private val unlockedFlow = MutableStateFlow(true)
        private val resultFlow = MutableStateFlow<TourRunResult?>(null)
        override val unlocked: Flow<Boolean> = unlockedFlow
        override val lastTourResult: Flow<TourRunResult?> = resultFlow
        override suspend fun setUnlocked(unlocked: Boolean) {
            unlockedFlow.value = unlocked
        }
        override suspend fun saveTourResult(result: TourRunResult) {
            resultFlow.value = result
        }
    }

    @Test
    fun overviewHome_tipMentionsStatusAndSections() {
        val step = ExperimentalCoreTourV1.definition.steps
            .first { it.id == TourStepId.OVERVIEW_HOME }
        assertEquals(R.string.tour_overview_home, step.tipResId)
        assertEquals(TourTargetId.HOME_OVERVIEW, step.targetId)
        val tip = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(step.tipResId)
        assertTrue(tip.contains("состояни"))
        assertFalse(tip.contains("пауз"))
        assertFalse(tip.contains("время следующего"))
        assertTrue(tip.contains("Архив"))
        assertTrue(tip.contains("Настройки"))
    }

    @Test
    fun exportShare_showOnly_callbacksStayZero_thenWorkAfterExit() = runTest {
        val controller = TourController(FakeStore(), clock = { 1L })
        var exportAll = 0
        var shareAll = 0

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(LocalTourController provides controller) {
                    val session by controller.session.collectAsStateWithLifecycle()
                    val tourActive = session.isActive
                    ArchiveHubScreen(
                        onOpenDays = {},
                        onOpenQuestions = {},
                        onOpenInsights = {},
                        onExportAll = { if (!tourActive) exportAll++ },
                        onExportPeriod = {},
                        onShareAll = { if (!tourActive) shareAll++ },
                        onSharePeriod = {},
                        onBack = {},
                        periodSelectionEnabled = true,
                    )
                }
            }
        }

        controller.start(
            TourDefinition(
                id = "export_safety",
                steps = listOf(
                    TourStep(
                        id = TourStepId.EXPORT_INFO,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.ARCHIVE_EXPORT_SECTION,
                        completion = TourCompletion.ManualAdvance,
                    ),
                ),
            ),
        )
        runCurrent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).performClick()
        assertEquals(0, exportAll)
        assertEquals(0, shareAll)

        controller.onExit(TourStepId.EXPORT_INFO)
        runCurrent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).performClick()
        assertEquals(1, exportAll)
        assertEquals(1, shareAll)
    }

    @Test
    fun settingsStyleGuards_schedulePauseBackupSoundSystem_zeroWhileActive() = runTest {
        val controller = TourController(FakeStore(), clock = { 1L })
        var schedule = 0
        var pause = 0
        var backup = 0
        var sound = 0
        var system = 0

        composeRule.setContent {
            PraktikaTheme {
                TourHost(
                    tourController = controller,
                    navController = rememberNavController(),
                ) {
                    val session by controller.session.collectAsStateWithLifecycle()
                    val tourActive = session.isActive
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(
                            onClick = { if (!tourActive) schedule++ },
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_SLOT_1),
                        ) { Text("schedule") }
                        TextButton(
                            onClick = { if (!tourActive) pause++ },
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_PAUSE_RESUME),
                        ) { Text("pause") }
                        TextButton(
                            onClick = { if (!tourActive) backup++ },
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_SECTION),
                        ) { Text("backup") }
                        Switch(
                            checked = true,
                            onCheckedChange = { if (!tourActive) sound++ },
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_SOUND_SWITCH),
                        )
                        TextButton(
                            onClick = { if (!tourActive) system++ },
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_NOTIFICATION_SETTINGS),
                        ) { Text("system") }
                    }
                }
            }
        }

        controller.start(
            TourDefinition(
                id = "settings_guards",
                steps = listOf(
                    TourStep(
                        id = TourStepId.SCHEDULE_INFO,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        completion = TourCompletion.ManualAdvance,
                    ),
                ),
            ),
        )
        runCurrent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_PAUSE_RESUME).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACKUP_SECTION).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SOUND_SWITCH).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_NOTIFICATION_SETTINGS).performClick()
        assertEquals(0, schedule)
        assertEquals(0, pause)
        assertEquals(0, backup)
        assertEquals(0, sound)
        assertEquals(0, system)

        controller.onExit(TourStepId.SCHEDULE_INFO)
        runCurrent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_PAUSE_RESUME).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACKUP_SECTION).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SOUND_SWITCH).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_NOTIFICATION_SETTINGS).performClick()
        assertEquals(1, schedule)
        assertEquals(1, pause)
        assertEquals(1, backup)
        assertEquals(1, sound)
        assertEquals(1, system)
    }
}
