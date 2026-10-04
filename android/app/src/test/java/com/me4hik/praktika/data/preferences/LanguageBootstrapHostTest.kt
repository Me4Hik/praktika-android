package com.me4hik.praktika.data.preferences

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.R
import com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * DataStore remains product SoT; AppCompat locales are the runtime mirror after bootstrap apply.
 * Match-path reconcile refreshes incomplete snapshot only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "ru")
class LanguageBootstrapHostTest {
    private lateinit var context: Context
    private lateinit var repository: LanguagePreferenceRepository
    private lateinit var database: PraktikaDatabase
    private lateinit var cycleRepository: CycleRepository
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private val wordingMode = AtomicReference(QuestionWordingMode.MASCULINE)
    private var notificationSyncCalls = 0

    @Before
    fun setUp() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        context = app
        app.setTheme(R.style.Theme_Praktika)
        app.praktikaPreferencesDataStore.edit { it.clear() }
        repository = DataStoreLanguagePreferenceRepository(app)
        AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()

        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedBaseData()
        cycleRepository = CycleRepository(
            database,
            FakeTimeProvider(epochAt(11, 0, 0), ZONE_KIEV),
            NoOpBackupMutationRequestSink,
            context,
            wordingModeSource = QuestionWordingModeSource { wordingMode.get() },
        )
        notificationSyncCalls = 0
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
        activityController.pause().stop().destroy()
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun existingUserDefault_appliesRussianToAppCompatMirror() = runBlocking {
        assertFalse(repository.languageSelected.first())
        repository.ensureExistingUserDefault(isPracticeStarted = true)
        assertTrue(repository.languageSelected.first())
        assertEquals(AppLanguage.RU, repository.language.first())

        assertEquals(LocaleApplyResult.CHANGED, AppLocaleController.apply(repository.language.first()))
        assertEquals("ru", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun freshSelection_uk_persistsAndMirrors() = runBlocking {
        repository.setLanguage(AppLanguage.UK)
        repository.markLanguageSelected()
        AppLocaleController.apply(AppLanguage.UK)

        assertEquals(AppLanguage.UK, repository.language.first())
        assertTrue(repository.languageSelected.first())
        assertEquals("uk", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun freshSelection_pl_persistsAndMirrors() = runBlocking {
        repository.setLanguage(AppLanguage.PL)
        repository.markLanguageSelected()
        AppLocaleController.apply(AppLanguage.PL)

        assertEquals(AppLanguage.PL, repository.language.first())
        assertEquals("pl", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun restartPersistence_readsDataStoreAndReappliesMirror() = runBlocking {
        repository.setLanguage(AppLanguage.EN)
        repository.markLanguageSelected()
        AppLocaleController.apply(AppLanguage.EN)
        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())

        // Simulate process death clearing in-memory AppCompat request, then bootstrap from DataStore.
        AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)

        val selected = repository.languageSelected.first()
        val language = repository.language.first()
        assertTrue(selected)
        AppLocaleController.apply(language)
        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun bootstrapMismatch_skipsSnapshotReconcile() = runBlocking {
        repository.setLanguage(AppLanguage.UK)
        repository.markLanguageSelected()
        insertIncomplete(questionId = 1, snapshot = "stale PL")
        insertTerminalAnswered(questionId = 1, snapshot = "history PL", occurrenceId = 200L)

        val outcome = LanguageBootstrapReconciler.run(
            isPracticeStarted = true,
            languagePreferenceRepository = repository,
            matchesLocale = AppLocaleController::matches,
            applyLocale = { AppLocaleController.apply(it) },
            applyAppLanguage = { cycleRepository.applyAppLanguage() },
            requestNotificationSync = { notificationSyncCalls++ },
        )

        assertEquals(LanguageBootstrapReconciler.Outcome.AwaitRecreate, outcome)
        assertEquals(0, notificationSyncCalls)
        assertEquals(
            "stale PL",
            database.questionOccurrenceDao().getIncompleteOrdered().single().questionTextSnapshot,
        )
        assertEquals(
            "history PL",
            database.questionOccurrenceDao().getById(200L)!!.questionTextSnapshot,
        )
    }

    @Test
    fun bootstrapMatch_refreshesIncompleteKeepsHistory() = runBlocking {
        repository.setLanguage(AppLanguage.RU)
        repository.markLanguageSelected()
        AppLocaleController.apply(AppLanguage.RU)
        insertIncomplete(questionId = 1, snapshot = "stale EN")
        insertTerminalAnswered(questionId = 1, snapshot = "history EN", occurrenceId = 201L)

        val outcome = LanguageBootstrapReconciler.run(
            isPracticeStarted = true,
            languagePreferenceRepository = repository,
            matchesLocale = AppLocaleController::matches,
            applyLocale = { AppLocaleController.apply(it) },
            applyAppLanguage = { cycleRepository.applyAppLanguage() },
            requestNotificationSync = { notificationSyncCalls++ },
        )

        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(LanguageBootstrapReconciler.Destination.HOME),
            outcome,
        )
        assertEquals(1, notificationSyncCalls)
        val expected = QuestionDisplayTextResolver.resolve(
            context.resources,
            1,
            QuestionWordingMode.MASCULINE,
        )
        assertEquals(
            expected,
            database.questionOccurrenceDao().getIncompleteOrdered().single().questionTextSnapshot,
        )
        assertEquals(
            "history EN",
            database.questionOccurrenceDao().getById(201L)!!.questionTextSnapshot,
        )
    }

    @Test
    fun bootstrapMatch_noIncomplete_isSafeNoOp() = runBlocking {
        repository.setLanguage(AppLanguage.EN)
        repository.markLanguageSelected()
        AppLocaleController.apply(AppLanguage.EN)

        val outcome = LanguageBootstrapReconciler.run(
            isPracticeStarted = false,
            languagePreferenceRepository = repository,
            matchesLocale = AppLocaleController::matches,
            applyLocale = { AppLocaleController.apply(it) },
            applyAppLanguage = { cycleRepository.applyAppLanguage() },
            requestNotificationSync = { notificationSyncCalls++ },
        )

        assertEquals(
            LanguageBootstrapReconciler.Outcome.Ready(
                LanguageBootstrapReconciler.Destination.ONBOARDING,
            ),
            outcome,
        )
        assertEquals(1, notificationSyncCalls)
        assertTrue(database.questionOccurrenceDao().getIncompleteOrdered().isEmpty())
    }

    private suspend fun insertIncomplete(questionId: Int, snapshot: String) {
        database.practiceStateDao().update(
            database.practiceStateDao().get()!!.copy(
                isPracticeStarted = true,
                currentCycleNumber = 1,
                nextCyclePosition = questionId % 21 + 1,
                practiceStartedAtEpochMillis = epochAt(11, 0, 0),
                lastProcessedAtEpochMillis = epochAt(11, 0, 0),
            ),
        )
        database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = snapshot,
                cycleNumber = 1,
                cyclePosition = questionId,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochAt(11, 0, 0),
                availableUntilEpochMillis = epochAt(15, 0, 0),
                openedAtEpochMillis = null,
                completedAtEpochMillis = null,
                status = QuestionOccurrenceStatus.AVAILABLE,
                zoneId = ZONE_KIEV,
            ),
        )
    }

    private suspend fun insertTerminalAnswered(
        questionId: Int,
        snapshot: String,
        occurrenceId: Long,
    ) {
        database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                id = occurrenceId,
                questionId = questionId,
                questionTextSnapshot = snapshot,
                cycleNumber = 2,
                cyclePosition = questionId,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochAt(8, 0, 0),
                availableUntilEpochMillis = epochAt(8, 1, 0),
                openedAtEpochMillis = epochAt(8, 0, 0),
                completedAtEpochMillis = epochAt(8, 0, 30),
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = ZONE_KIEV,
            ),
        )
    }

    private suspend fun seedBaseData() {
        val questions = (1..21).map { index ->
            QuestionEntity(
                id = index,
                cyclePosition = index,
                text = "Question $index",
                isActive = true,
            )
        }
        database.questionDao().insertAll(questions)
        database.scheduleSlotDao().insertAll(
            listOf(
                ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660),
                ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 900),
                ScheduleSlotEntity(slotIndex = 3, timeOfDayMinutes = 1140),
            ),
        )
        database.practiceStateDao().insert(
            PracticeStateEntity(
                id = 1,
                isPracticeStarted = false,
                isPaused = false,
                practiceStartedAtEpochMillis = null,
                currentCycleNumber = 0,
                nextCyclePosition = 1,
                lastProcessedAtEpochMillis = null,
                pausedAtEpochMillis = null,
                activeZoneId = ZONE_KIEV,
                seedVersion = 1,
            ),
        )
    }

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2024, 6, 1, hour, minute, second, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kyiv"
    }
}
