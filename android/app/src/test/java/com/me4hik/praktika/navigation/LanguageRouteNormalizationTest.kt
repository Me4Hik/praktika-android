package com.me4hik.praktika.navigation

import com.me4hik.praktika.data.preferences.LanguageBootstrapReconciler
import com.me4hik.praktika.data.preferences.LanguageChooserContinue
import com.me4hik.praktika.data.preferences.LocaleApplyResult
import com.me4hik.praktika.data.preferences.languageChooserContinueAfterApply
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locale-20 Option C: stale LANGUAGE restore vs bootstrap computed destination.
 */
class LanguageRouteNormalizationTest {

    @Test
    fun restoredLanguage_computedOnboarding_normalizes() {
        assertEquals(
            LanguageRouteNormalization.ReplaceLanguageRoot(Routes.ONBOARDING),
            languageRouteNormalization(
                computedDestination = Routes.ONBOARDING,
                currentRoute = Routes.LANGUAGE,
            ),
        )
    }

    @Test
    fun restoredLanguage_computedHome_normalizes() {
        assertEquals(
            LanguageRouteNormalization.ReplaceLanguageRoot(Routes.HOME),
            languageRouteNormalization(
                computedDestination = Routes.HOME,
                currentRoute = Routes.LANGUAGE,
            ),
        )
    }

    @Test
    fun restoredLanguage_computedLanguage_noOp() {
        assertEquals(
            LanguageRouteNormalization.NoOp,
            languageRouteNormalization(
                computedDestination = Routes.LANGUAGE,
                currentRoute = Routes.LANGUAGE,
            ),
        )
    }

    @Test
    fun restoredQuestion_computedHome_noOp() {
        assertEquals(
            LanguageRouteNormalization.NoOp,
            languageRouteNormalization(
                computedDestination = Routes.HOME,
                currentRoute = Routes.question(42L),
            ),
        )
    }

    @Test
    fun restoredSettings_computedHome_noOp() {
        assertEquals(
            LanguageRouteNormalization.NoOp,
            languageRouteNormalization(
                computedDestination = Routes.HOME,
                currentRoute = Routes.SETTINGS,
            ),
        )
    }

    @Test
    fun restoredArchive_computedHome_noOp() {
        assertEquals(
            LanguageRouteNormalization.NoOp,
            languageRouteNormalization(
                computedDestination = Routes.HOME,
                currentRoute = Routes.ARCHIVE,
            ),
        )
    }

    @Test
    fun currentAlreadyOnboarding_noOp() {
        assertEquals(
            LanguageRouteNormalization.NoOp,
            languageRouteNormalization(
                computedDestination = Routes.ONBOARDING,
                currentRoute = Routes.ONBOARDING,
            ),
        )
    }

    @Test
    fun currentAlreadyHome_noOp() {
        assertEquals(
            LanguageRouteNormalization.NoOp,
            languageRouteNormalization(
                computedDestination = Routes.HOME,
                currentRoute = Routes.HOME,
            ),
        )
    }

    @Test
    fun currentRouteNull_noOp() {
        assertEquals(
            LanguageRouteNormalization.NoOp,
            languageRouteNormalization(
                computedDestination = Routes.ONBOARDING,
                currentRoute = null,
            ),
        )
    }

    @Test
    fun unchangedChooserContinue_stillReentersBootstrap() {
        // Locale-17 path must remain ReenterBootstrap (tear down NavHost + bootstrapEpoch).
        assertEquals(
            LanguageChooserContinue.ReenterBootstrap,
            languageChooserContinueAfterApply(LocaleApplyResult.UNCHANGED),
        )
    }

    @Test
    fun changedChooserPath_awaitsRecreate_thenStaleLanguageNormalizedToOnboarding() {
        // CHANGED still awaits recreate (no direct nav from chooser).
        assertEquals(
            LanguageChooserContinue.AwaitActivityRecreate,
            languageChooserContinueAfterApply(LocaleApplyResult.CHANGED),
        )
        // Bootstrap Ready maps Destination.ONBOARDING → Routes.ONBOARDING; restore may
        // still show LANGUAGE — Option C replaces it so chooser cannot stick.
        assertEquals(Routes.ONBOARDING, routeForBootstrapDestination(LanguageBootstrapReconciler.Destination.ONBOARDING))
        assertEquals(
            LanguageRouteNormalization.ReplaceLanguageRoot(Routes.ONBOARDING),
            languageRouteNormalization(
                computedDestination = Routes.ONBOARDING,
                currentRoute = Routes.LANGUAGE,
            ),
        )
    }

    private fun routeForBootstrapDestination(
        destination: LanguageBootstrapReconciler.Destination,
    ): String = when (destination) {
        LanguageBootstrapReconciler.Destination.LANGUAGE -> Routes.LANGUAGE
        LanguageBootstrapReconciler.Destination.ONBOARDING -> Routes.ONBOARDING
        LanguageBootstrapReconciler.Destination.HOME -> Routes.HOME
    }
}
