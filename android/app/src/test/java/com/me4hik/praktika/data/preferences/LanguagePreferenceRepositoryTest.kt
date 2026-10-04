package com.me4hik.praktika.data.preferences

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LanguagePreferenceRepositoryTest {
    private lateinit var repository: LanguagePreferenceRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.praktikaPreferencesDataStore.edit { it.clear() }
        repository = DataStoreLanguagePreferenceRepository(context)
    }

    @Test
    fun defaultsUnselectedAndRussian() = runBlocking {
        assertFalse(repository.languageSelected.first())
        assertEquals(AppLanguage.RU, repository.language.first())
    }

    @Test
    fun setLanguageAndMarkSelectedRoundTrip() = runBlocking {
        repository.setLanguage(AppLanguage.UK)
        repository.markLanguageSelected()
        assertEquals(AppLanguage.UK, repository.language.first())
        assertTrue(repository.languageSelected.first())
    }

    @Test
    fun ensureExistingUserDefault_setsRussianWhenPracticeStarted() = runBlocking {
        assertFalse(repository.languageSelected.first())
        repository.ensureExistingUserDefault(isPracticeStarted = true)
        assertTrue(repository.languageSelected.first())
        assertEquals(AppLanguage.RU, repository.language.first())
    }

    @Test
    fun ensureExistingUserDefault_leavesUnselectedWhenPracticeNotStarted() = runBlocking {
        repository.ensureExistingUserDefault(isPracticeStarted = false)
        assertFalse(repository.languageSelected.first())
    }

    @Test
    fun ensureExistingUserDefault_noopWhenAlreadySelected() = runBlocking {
        repository.setLanguage(AppLanguage.PL)
        repository.markLanguageSelected()
        repository.ensureExistingUserDefault(isPracticeStarted = true)
        assertEquals(AppLanguage.PL, repository.language.first())
        assertTrue(repository.languageSelected.first())
    }
}
