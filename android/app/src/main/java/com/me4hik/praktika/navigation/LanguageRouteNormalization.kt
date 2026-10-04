package com.me4hik.praktika.navigation

/**
 * Locale-20: after locale CHANGED Activity recreate, bootstrap may compute ONBOARDING/HOME
 * while restored [androidx.navigation.NavController] still sits on [Routes.LANGUAGE].
 *
 * Only that gate mismatch is corrected. Ordinary restored app routes are left alone.
 */
sealed class LanguageRouteNormalization {
    data object NoOp : LanguageRouteNormalization()

    /** Replace stale LANGUAGE root with [targetRoute] (ONBOARDING or HOME). */
    data class ReplaceLanguageRoot(val targetRoute: String) : LanguageRouteNormalization()
}

/**
 * @param computedDestination bootstrap Ready route ([Routes.LANGUAGE] / ONBOARDING / HOME)
 * @param currentRoute NavController current destination route, or null if not ready yet
 */
fun languageRouteNormalization(
    computedDestination: String,
    currentRoute: String?,
): LanguageRouteNormalization {
    if (currentRoute == null) {
        return LanguageRouteNormalization.NoOp
    }
    if (computedDestination != Routes.ONBOARDING && computedDestination != Routes.HOME) {
        return LanguageRouteNormalization.NoOp
    }
    if (currentRoute == computedDestination) {
        return LanguageRouteNormalization.NoOp
    }
    if (currentRoute != Routes.LANGUAGE) {
        return LanguageRouteNormalization.NoOp
    }
    return LanguageRouteNormalization.ReplaceLanguageRoot(computedDestination)
}
