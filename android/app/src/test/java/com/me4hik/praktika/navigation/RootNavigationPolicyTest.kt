// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - unit tests корневой навигации
package com.me4hik.praktika.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootNavigationPolicyTest {
    @Test
    fun notStartedOnboardingDoesNotNavigate() {
        assertFalse(
            RootNavigationPolicy.shouldNavigateOnboardingToHome(
                currentRoute = Routes.ONBOARDING,
                isPracticeStarted = false,
            ),
        )
    }

    @Test
    fun startedOnboardingNavigatesToHome() {
        assertTrue(
            RootNavigationPolicy.shouldNavigateOnboardingToHome(
                currentRoute = Routes.ONBOARDING,
                isPracticeStarted = true,
            ),
        )
    }

    @Test
    fun startedHomeIsNoOp() {
        assertFalse(
            RootNavigationPolicy.shouldNavigateOnboardingToHome(
                currentRoute = Routes.HOME,
                isPracticeStarted = true,
            ),
        )
    }

    @Test
    fun startedArchiveIsNoOp() {
        assertFalse(
            RootNavigationPolicy.shouldNavigateOnboardingToHome(
                currentRoute = Routes.ARCHIVE,
                isPracticeStarted = true,
            ),
        )
        assertTrue(RootNavigationPolicy.isSecondaryRoute(Routes.ARCHIVE))
        assertTrue(RootNavigationPolicy.isSecondaryRoute(Routes.ARCHIVE_QUESTIONS))
        assertTrue(RootNavigationPolicy.isSecondaryRoute(Routes.ARCHIVE_INSIGHTS))
    }

    @Test
    fun startedSettingsIsNoOp() {
        assertFalse(
            RootNavigationPolicy.shouldNavigateOnboardingToHome(
                currentRoute = Routes.SETTINGS,
                isPracticeStarted = true,
            ),
        )
        assertTrue(RootNavigationPolicy.isSecondaryRoute(Routes.SETTINGS))
        assertTrue(RootNavigationPolicy.isSecondaryRoute(Routes.SOUND_LIBRARY))
    }

    @Test
    fun startedQuestionIsNoOp() {
        val questionRoute = Routes.question(10L)
        assertFalse(
            RootNavigationPolicy.shouldNavigateOnboardingToHome(
                currentRoute = questionRoute,
                isPracticeStarted = true,
            ),
        )
        assertTrue(RootNavigationPolicy.isSecondaryRoute(questionRoute))
    }
}
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
