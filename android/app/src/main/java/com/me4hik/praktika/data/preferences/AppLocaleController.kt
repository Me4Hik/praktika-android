package com.me4hik.praktika.data.preferences

import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Result of [AppLocaleController.apply].
 * [CHANGED] means AppCompat may recreate the Activity; callers must not rely on code after apply.
 */
enum class LocaleApplyResult {
    CHANGED,
    UNCHANGED,
}

/**
 * Runtime/platform mirror for the product language preference (DataStore SoT).
 * Call [apply] on the main thread after AppCompat Activity/theme wiring is active.
 */
object AppLocaleController {
    fun matches(language: AppLanguage): Boolean =
        currentLanguageTags() == language.tag

    fun apply(language: AppLanguage): LocaleApplyResult {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "AppLocaleController.apply must run on the main thread"
        }
        if (matches(language)) {
            return LocaleApplyResult.UNCHANGED
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        return LocaleApplyResult.CHANGED
    }

    fun currentLocale(): Locale {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            return locales[0] ?: Locale.getDefault()
        }
        return Locale.getDefault()
    }

    fun currentLanguageTags(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()
}
