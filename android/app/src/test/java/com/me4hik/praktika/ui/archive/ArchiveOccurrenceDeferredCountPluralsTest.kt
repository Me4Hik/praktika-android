// 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik START - plurals 1/2/5
package com.me4hik.praktika.ui.archive

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ArchiveOccurrenceDeferredCountPluralsTest {
    @Test
    fun quantityForms_oneTwoFive_russian() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale("ru"))
        val context = base.createConfigurationContext(config)
        val resources = context.resources
        assertEquals(
            "Отложено 1 раз",
            resources.getQuantityString(R.plurals.archive_occurrence_deferred_count, 1, 1),
        )
        assertEquals(
            "Отложено 2 раза",
            resources.getQuantityString(R.plurals.archive_occurrence_deferred_count, 2, 2),
        )
        assertEquals(
            "Отложено 5 раз",
            resources.getQuantityString(R.plurals.archive_occurrence_deferred_count, 5, 5),
        )
    }
}
// 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik END
