// 06.08.2026 Settings Schedule cursor by Me4Hik START - instrumented tests sound preference
package com.me4hik.praktika.data.preferences

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundPreferenceRepositoryTest {
    @Test
    fun defaultSoundEnabledIsTrue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = DataStoreSoundPreferenceRepository(context)
        repository.setSoundEnabled(true)
        assertTrue(repository.soundEnabled.first())
    }

    @Test
    fun setFalsePersistsInNewInstance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = DataStoreSoundPreferenceRepository(context)
        first.setSoundEnabled(false)
        val second = DataStoreSoundPreferenceRepository(context)
        assertFalse(second.soundEnabled.first())
    }
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END
