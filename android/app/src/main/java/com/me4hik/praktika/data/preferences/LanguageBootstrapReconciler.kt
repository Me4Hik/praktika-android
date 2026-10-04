package com.me4hik.praktika.data.preferences

import kotlinx.coroutines.flow.first

/**
 * Two-phase language bootstrap under the Loading gate.
 *
 * Phase 1 (locale mismatch): apply platform locale and stop — Activity recreate will re-enter.
 * Phase 2 (locale match): reconcile incomplete snapshot + notifications, then expose destination.
 *
 * Code after [LocaleApplyResult.CHANGED] is never treated as guaranteed.
 */
object LanguageBootstrapReconciler {

    sealed class Outcome {
        /** Platform locale was updated; caller must stay on Loading and await recreate. */
        data object AwaitRecreate : Outcome()

        /** Locale settled (or chooser needed); destination may become visible. */
        data class Ready(val destination: Destination) : Outcome()
    }

    enum class Destination {
        LANGUAGE,
        ONBOARDING,
        HOME,
    }

    /**
     * @param matchesLocale pure check against current AppCompat/platform tags
     * @param applyLocale must hop to Main; returns whether tags changed
     * @param applyAppLanguage incomplete snapshot refresh (idempotent)
     * @param requestNotificationSync runs only after snapshot reconcile on match path
     */
    suspend fun run(
        isPracticeStarted: Boolean,
        languagePreferenceRepository: LanguagePreferenceRepository,
        matchesLocale: (AppLanguage) -> Boolean,
        applyLocale: suspend (AppLanguage) -> LocaleApplyResult,
        applyAppLanguage: suspend () -> Boolean,
        requestNotificationSync: suspend () -> Unit,
    ): Outcome {
        languagePreferenceRepository.ensureExistingUserDefault(isPracticeStarted = isPracticeStarted)
        val selected = languagePreferenceRepository.languageSelected.first()
        val language = languagePreferenceRepository.language.first()

        if (!selected) {
            return Outcome.Ready(Destination.LANGUAGE)
        }

        if (!matchesLocale(language)) {
            applyLocale(language)
            // Do not reconcile after CHANGED — recreate owns the next pass.
            return Outcome.AwaitRecreate
        }

        applyAppLanguage()
        requestNotificationSync()

        return Outcome.Ready(
            if (isPracticeStarted) Destination.HOME else Destination.ONBOARDING,
        )
    }
}
