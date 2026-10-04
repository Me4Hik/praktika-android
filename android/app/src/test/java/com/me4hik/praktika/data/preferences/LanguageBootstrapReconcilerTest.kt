package com.me4hik.praktika.data.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Focused state-machine coverage for two-phase language bootstrap (Locale-14).
 */
class LanguageBootstrapReconcilerTest {

    private lateinit var repository: FakeLanguagePreferenceRepository
    private val applyLocaleCalls = mutableListOf<AppLanguage>()
    private var applyAppLanguageCalls = 0
    private var notificationSyncCalls = 0
    private var platformTag: String = ""

    @Before
    fun setUp() {
        repository = FakeLanguagePreferenceRepository()
        applyLocaleCalls.clear()
        applyAppLanguageCalls = 0
        notificationSyncCalls = 0
        platformTag = ""
    }

    @Test
    fun localeMismatch_appliesLocaleAndSkipsReconcile() = runBlocking {
        repository.setLanguage(AppLanguage.UK)
        repository.markLanguageSelected()
        platformTag = "pl"

        val outcome = runBootstrap(isPracticeStarted = true)

        assertEquals(LanguageBootstrapReconciler.Outcome.AwaitRecreate, outcome)
        assertEquals(listOf(AppLanguage.UK), applyLocaleCalls)
        assertEquals(0, applyAppLanguageCalls)
        assertEquals(0, notificationSyncCalls)
    }

    @Test
    fun localeMatch_refreshesSnapshotThenSyncThenHome() = runBlocking {
        repository.setLanguage(AppLanguage.UK)
        repository.markLanguageSelected()
        platformTag = "uk"

        val outcome = runBootstrap(isPracticeStarted = true)

        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(LanguageBootstrapReconciler.Destination.HOME),
            outcome,
        )
        assertTrue(applyLocaleCalls.isEmpty())
        assertEquals(1, applyAppLanguageCalls)
        assertEquals(1, notificationSyncCalls)
    }

    @Test
    fun processDeath_secondPassAfterPersistRepairsState() = runBlocking {
        repository.setLanguage(AppLanguage.EN)
        repository.markLanguageSelected()
        platformTag = "" // process lost AppCompat mirror

        val first = runBootstrap(isPracticeStarted = true)
        assertEquals(LanguageBootstrapReconciler.Outcome.AwaitRecreate, first)
        assertEquals(0, applyAppLanguageCalls)
        assertEquals(0, notificationSyncCalls)

        // Recreate: platform now mirrors DataStore.
        platformTag = "en"
        applyLocaleCalls.clear()

        val second = runBootstrap(isPracticeStarted = true)
        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(LanguageBootstrapReconciler.Destination.HOME),
            second,
        )
        assertTrue(applyLocaleCalls.isEmpty())
        assertEquals(1, applyAppLanguageCalls)
        assertEquals(1, notificationSyncCalls)
    }

    @Test
    fun rapidSwitch_finalSnapshotUsesLastPersistedLanguage() = runBlocking {
        val languages = listOf(AppLanguage.RU, AppLanguage.EN, AppLanguage.UK, AppLanguage.PL)
        var lastApplied: AppLanguage? = null
        for (language in languages) {
            repository.setLanguage(language)
            repository.markLanguageSelected()
            platformTag = language.tag // each recreate settles to last persist
            lastApplied = language
            applyAppLanguageCalls = 0
            notificationSyncCalls = 0
            val outcome = runBootstrap(isPracticeStarted = true)
            assertEquals(
                LanguageBootstrapReconciler.Outcome.Ready(LanguageBootstrapReconciler.Destination.HOME),
                outcome,
            )
            assertEquals(1, applyAppLanguageCalls)
            assertEquals(1, notificationSyncCalls)
        }
        assertEquals(AppLanguage.PL, lastApplied)
        assertEquals(AppLanguage.PL, repository.languageState.value)
    }

    @Test
    fun idempotentColdStart_matchPathRunsReconcileAgainSafely() = runBlocking {
        repository.setLanguage(AppLanguage.PL)
        repository.markLanguageSelected()
        platformTag = "pl"

        val first = runBootstrap(isPracticeStarted = true)
        val second = runBootstrap(isPracticeStarted = true)

        assertEquals(first, second)
        assertEquals(2, applyAppLanguageCalls)
        assertEquals(2, notificationSyncCalls)
    }

    @Test
    fun noIncomplete_applyAppLanguageNoOpStillReady() = runBlocking {
        repository.setLanguage(AppLanguage.RU)
        repository.markLanguageSelected()
        platformTag = "ru"
        var applyReturnedFalse = false

        val outcome = LanguageBootstrapReconciler.run(
            isPracticeStarted = true,
            languagePreferenceRepository = repository,
            matchesLocale = { it.tag == platformTag },
            applyLocale = {
                applyLocaleCalls += it
                LocaleApplyResult.CHANGED
            },
            applyAppLanguage = {
                applyAppLanguageCalls++
                applyReturnedFalse = true
                false
            },
            requestNotificationSync = { notificationSyncCalls++ },
        )

        assertTrue(applyReturnedFalse)
        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(LanguageBootstrapReconciler.Destination.HOME),
            outcome,
        )
        assertEquals(1, notificationSyncCalls)
    }

    @Test
    fun notStarted_selectedLocale_opensOnboarding() = runBlocking {
        repository.setLanguage(AppLanguage.EN)
        repository.markLanguageSelected()
        platformTag = "en"

        val outcome = runBootstrap(isPracticeStarted = false)

        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(
                LanguageBootstrapReconciler.Destination.ONBOARDING,
            ),
            outcome,
        )
        assertEquals(1, applyAppLanguageCalls)
        assertEquals(1, notificationSyncCalls)
    }

    @Test
    fun unselected_opensLanguageChooserWithoutReconcile() = runBlocking {
        val outcome = runBootstrap(isPracticeStarted = false)

        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(
                LanguageBootstrapReconciler.Destination.LANGUAGE,
            ),
            outcome,
        )
        assertTrue(repository.ensureCalled)
        assertFalse(repository.selectedState.value)
        assertTrue(applyLocaleCalls.isEmpty())
        assertEquals(0, applyAppLanguageCalls)
        assertEquals(0, notificationSyncCalls)
    }

    @Test
    fun freshChooserUnchangedPath_secondBootstrapOpensOnboarding() = runBlocking {
        // First pass: unselected → LANGUAGE (platform may already be EN).
        platformTag = "en"
        val first = runBootstrap(isPracticeStarted = false)
        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(
                LanguageBootstrapReconciler.Destination.LANGUAGE,
            ),
            first,
        )
        assertEquals(0, applyAppLanguageCalls)

        // User picks English: persist selected; apply returns UNCHANGED → re-enter bootstrap.
        repository.setLanguage(AppLanguage.EN)
        repository.markLanguageSelected()
        assertEquals(
            LanguageChooserContinue.ReenterBootstrap,
            languageChooserContinueAfterApply(LocaleApplyResult.UNCHANGED),
        )

        val second = runBootstrap(isPracticeStarted = false)
        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(
                LanguageBootstrapReconciler.Destination.ONBOARDING,
            ),
            second,
        )
        assertTrue(applyLocaleCalls.isEmpty())
        assertEquals(1, applyAppLanguageCalls)
        assertEquals(1, notificationSyncCalls)
    }

    @Test
    fun changedPath_mismatchStillAwaitsRecreate() = runBlocking {
        repository.setLanguage(AppLanguage.PL)
        repository.markLanguageSelected()
        platformTag = "en"

        val outcome = runBootstrap(isPracticeStarted = false)
        assertEquals(LanguageBootstrapReconciler.Outcome.AwaitRecreate, outcome)
        assertEquals(
            LanguageChooserContinue.AwaitActivityRecreate,
            languageChooserContinueAfterApply(LocaleApplyResult.CHANGED),
        )
        assertEquals(0, applyAppLanguageCalls)
    }

    @Test
    fun stickyMatchingLocale_startedReconcilesSnapshot() = runBlocking {
        repository.setLanguage(AppLanguage.UK)
        repository.markLanguageSelected()
        platformTag = "uk"

        val first = runBootstrap(isPracticeStarted = true)
        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(LanguageBootstrapReconciler.Destination.HOME),
            first,
        )
        val second = runBootstrap(isPracticeStarted = true)
        assertEquals(first, second)
        assertEquals(2, applyAppLanguageCalls)
        assertEquals(2, notificationSyncCalls)
    }

    @Test
    fun existingUserDefault_setsSelectedBeforeMatchPath() = runBlocking {
        // Practice started + unselected → ensureExistingUserDefault selects RU.
        platformTag = "ru"
        repository.ensureWillSelectDefault = true

        val outcome = runBootstrap(isPracticeStarted = true)

        assertTrue(repository.ensureCalled)
        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(LanguageBootstrapReconciler.Destination.HOME),
            outcome,
        )
        assertEquals(AppLanguage.RU, repository.languageState.value)
        assertEquals(1, applyAppLanguageCalls)
    }

    private suspend fun runBootstrap(isPracticeStarted: Boolean) =
        LanguageBootstrapReconciler.run(
            isPracticeStarted = isPracticeStarted,
            languagePreferenceRepository = repository,
            matchesLocale = { it.tag == platformTag },
            applyLocale = { language ->
                applyLocaleCalls += language
                platformTag = language.tag
                LocaleApplyResult.CHANGED
            },
            applyAppLanguage = {
                applyAppLanguageCalls++
                true
            },
            requestNotificationSync = { notificationSyncCalls++ },
        )

    private class FakeLanguagePreferenceRepository : LanguagePreferenceRepository {
        val languageState = MutableStateFlow(AppLanguage.DEFAULT)
        val selectedState = MutableStateFlow(false)
        var ensureCalled = false
        var ensureWillSelectDefault = false

        override val language: Flow<AppLanguage> = languageState
        override val languageSelected: Flow<Boolean> = selectedState

        override suspend fun setLanguage(language: AppLanguage) {
            languageState.value = language
        }

        override suspend fun markLanguageSelected() {
            selectedState.value = true
        }

        override suspend fun ensureExistingUserDefault(isPracticeStarted: Boolean) {
            ensureCalled = true
            if (ensureWillSelectDefault && isPracticeStarted && !selectedState.value) {
                languageState.value = AppLanguage.RU
                selectedState.value = true
            }
        }
    }
}
