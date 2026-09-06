package com.me4hik.praktika.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsNotificationsNavigationStackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settings_toNotifications_toSoundLibrary_backsInOrder() {
        lateinit var currentRoute: () -> String?
        composeRule.setContent {
            val navController = rememberNavController()
            val entry by navController.currentBackStackEntryAsState()
            currentRoute = { entry?.destination?.route }
            NavHost(
                navController = navController,
                startDestination = Routes.SETTINGS,
            ) {
                composable(Routes.SETTINGS) {
                    Text(
                        text = "settings",
                        modifier = Modifier
                            .testTag("nav_settings")
                            .clickable {
                                navController.navigate(Routes.SETTINGS_NOTIFICATIONS)
                            },
                    )
                }
                composable(Routes.SETTINGS_NOTIFICATIONS) {
                    Text(
                        text = "notifications",
                        modifier = Modifier
                            .testTag("nav_notifications")
                            .clickable {
                                navController.navigate(Routes.SOUND_LIBRARY)
                            },
                    )
                }
                composable(Routes.SOUND_LIBRARY) {
                    Text(
                        text = "sound",
                        modifier = Modifier.testTag("nav_sound"),
                    )
                }
            }
        }

        assertEquals(Routes.SETTINGS, currentRoute())
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("nav_notifications").assertIsDisplayed()
        assertEquals(Routes.SETTINGS_NOTIFICATIONS, currentRoute())

        composeRule.onNodeWithTag("nav_notifications").performClick()
        composeRule.onNodeWithTag("nav_sound").assertIsDisplayed()
        assertEquals(Routes.SOUND_LIBRARY, currentRoute())

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_notifications").assertIsDisplayed()
        assertEquals(Routes.SETTINGS_NOTIFICATIONS, currentRoute())

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_settings").assertIsDisplayed()
        assertEquals(Routes.SETTINGS, currentRoute())
    }
}
