package com.me4hik.praktika.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class QuestionWordingPreferenceRepositoryTest {
    @Test
    fun defaultAndPersistRoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DataStoreQuestionWordingPreferenceRepository(context)
        assertEquals(QuestionWordingMode.MASCULINE, repository.wordingMode.first())
        repository.setWordingMode(QuestionWordingMode.NEUTRAL)
        assertEquals(QuestionWordingMode.NEUTRAL, repository.wordingMode.first())
        repository.setWordingMode(QuestionWordingMode.FEMININE)
        assertEquals(QuestionWordingMode.FEMININE, repository.wordingMode.first())
    }
}
