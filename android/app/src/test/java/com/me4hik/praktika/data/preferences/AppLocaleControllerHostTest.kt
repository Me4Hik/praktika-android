package com.me4hik.praktika.data.preferences

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.R
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Proves AppCompatDelegate locale path is active when the host is AppCompatActivity + AppCompat theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppLocaleControllerHostTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var context: Context

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.setTheme(R.style.Theme_Praktika)
        AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        context = activityController.get()
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
        activityController.pause().stop().destroy()
    }

    @Test
    fun apply_setsAppCompatApplicationLocales() {
        assertEquals(LocaleApplyResult.CHANGED, AppLocaleController.apply(AppLanguage.UK))
        assertEquals("uk", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertEquals("uk", AppLocaleController.currentLanguageTags())
        assertEquals("uk", AppLocaleController.currentLocale().language)
        assertTrue(AppLocaleController.matches(AppLanguage.UK))
    }

    @Test
    fun apply_settingsSwitchRoundTrip_ruEnUkPl() {
        listOf(AppLanguage.RU, AppLanguage.EN, AppLanguage.UK, AppLanguage.PL).forEach { language ->
            AppLocaleController.apply(language)
            assertEquals(language.tag, AppCompatDelegate.getApplicationLocales().toLanguageTags())
        }
        AppLocaleController.apply(AppLanguage.RU)
        assertEquals("ru", AppLocaleController.currentLanguageTags())
    }

    @Test
    fun apply_updatesResourceResolutionForOnboardingChrome() {
        AppLocaleController.apply(AppLanguage.EN)
        val en = localizedResources(Locale.ENGLISH)
        assertEquals("Start practice", en.getString(R.string.onboarding_start_practice))

        AppLocaleController.apply(AppLanguage.PL)
        val pl = localizedResources(Locale("pl"))
        assertEquals("Rozpocznij praktykę", pl.getString(R.string.onboarding_start_practice))

        AppLocaleController.apply(AppLanguage.UK)
        val uk = localizedResources(Locale("uk"))
        assertEquals("Почати практику", uk.getString(R.string.onboarding_start_practice))

        AppLocaleController.apply(AppLanguage.RU)
        val ru = localizedResources(Locale("ru"))
        assertEquals("Начать практику", ru.getString(R.string.onboarding_start_practice))
    }

    @Test
    fun apply_notificationStringsFollowLocale() {
        AppLocaleController.apply(AppLanguage.EN)
        val en = localizedResources(Locale.ENGLISH)
        assertEquals("Answer", en.getString(R.string.notification_action_answer))

        AppLocaleController.apply(AppLanguage.RU)
        val ru = localizedResources(Locale("ru"))
        assertEquals("Ответить", ru.getString(R.string.notification_action_answer))
    }

    @Test
    fun apply_isIdempotentForSameLanguage() {
        assertEquals(LocaleApplyResult.CHANGED, AppLocaleController.apply(AppLanguage.EN))
        assertEquals(LocaleApplyResult.UNCHANGED, AppLocaleController.apply(AppLanguage.EN))
        assertEquals("en", AppLocaleController.currentLanguageTags())
        assertTrue(AppCompatDelegate.getApplicationLocales().toLanguageTags().isNotEmpty())
    }

    private fun localizedResources(locale: Locale) =
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) },
        ).resources
}
