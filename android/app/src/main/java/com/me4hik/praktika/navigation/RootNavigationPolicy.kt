// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - правила корневого state-driven перехода
package com.me4hik.praktika.navigation

object RootNavigationPolicy {
    fun shouldNavigateOnboardingToHome(
        currentRoute: String?,
        isPracticeStarted: Boolean,
    ): Boolean = isPracticeStarted && currentRoute == Routes.ONBOARDING

    fun isRootRoute(currentRoute: String?): Boolean {
        return currentRoute == Routes.ONBOARDING || currentRoute == Routes.HOME
    }

    fun isSecondaryRoute(currentRoute: String?): Boolean {
        return when (currentRoute) {
            Routes.ARCHIVE,
            Routes.ARCHIVE_DAYS,
            Routes.ARCHIVE_QUESTIONS,
            Routes.ARCHIVE_INSIGHTS,
            Routes.QUESTION_HISTORY,
            Routes.SETTINGS,
            Routes.SOUND_LIBRARY,
            -> true
            else -> currentRoute?.startsWith("question/") == true ||
                currentRoute?.startsWith("answer/") == true ||
                currentRoute?.startsWith("archive/day/") == true ||
                // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - secondary route history
                currentRoute?.startsWith("archive/question/") == true ||
                // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
                // PROMPT 157 — missed drill-down under insights
                currentRoute?.startsWith("archive/insights/missed/") == true
        }
    }
}
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
