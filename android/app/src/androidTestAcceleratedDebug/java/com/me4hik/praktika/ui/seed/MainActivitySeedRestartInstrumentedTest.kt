// 05.08.2026 Idempotent Seed Fix cursor by Me4Hik START - Activity cold-start без seed crash
package com.me4hik.praktika.ui.seed

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class MainActivitySeedRestartInstrumentedTest {
    private val sandboxReset = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                resetAcceleratedSandbox()
                base.evaluate()
                PraktikaRuntimeHolder.resetForTests()
                MainActivityInitGate.resetForTests()
            }
        }
    }

    @get:Rule
    val sandboxResetRule = sandboxReset

    @Test
    fun coldRelaunchAfterStartPracticeDoesNotThrowSeedCorruption() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        ActivityScenario.launch(MainActivity::class.java).closeAfter {
            MainActivityInitGate.awaitInit()
            PraktikaRuntimeHolder.get(context).cycleRepository.startPractice()
            assertEquals(1, PraktikaRuntimeHolder.get(context).database.questionOccurrenceDao().count())
        }

        ActivityScenario.launch(MainActivity::class.java).closeAfter {
            MainActivityInitGate.awaitInit()
            val runtime = PraktikaRuntimeHolder.get(context)
            assertEquals(1, runtime.database.questionOccurrenceDao().count())
            assertTrue(runtime.database.practiceStateDao().get()!!.isPracticeStarted)
            runtime.cycleRepository.reconcile()
        }
    }

    private suspend inline fun ActivityScenario<MainActivity>.closeAfter(block: suspend () -> Unit) {
        try {
            block()
        } finally {
            close()
        }
    }

    private fun resetAcceleratedSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }
            ?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }
}
// 05.08.2026 Idempotent Seed Fix cursor by Me4Hik END
