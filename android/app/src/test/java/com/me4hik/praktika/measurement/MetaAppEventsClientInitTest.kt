package com.me4hik.praktika.measurement

import android.app.Application
import android.content.Context
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MetaAppEventsClientInitTest {

    private class RecordingBootstrap : MetaSdkBootstrap {
        val calls = mutableListOf<String>()
        var loggerCreations = 0

        override fun fullyInitialize() {
            calls += "fullyInitialize"
        }

        override fun activateApp(application: Application) {
            calls += "activateApp"
        }

        override fun newLogger(context: Context): MetaSdkEventLogger {
            loggerCreations += 1
            calls += "newLogger"
            return object : MetaSdkEventLogger {
                override fun logEvent(name: String) {
                    calls += "logEvent:$name"
                }

                override fun logEvent(name: String, parameters: Bundle) {
                    calls += "logEvent:$name:params"
                }
            }
        }
    }

    @Test
    fun initSequence_fullyInitialize_thenActivateApp_beforeFirstLogEvent() {
        val bootstrap = RecordingBootstrap()
        val client = DefaultMetaAppEventsClient(
            context = RuntimeEnvironment.getApplication(),
            sdk = bootstrap,
        )

        client.logEvent(AnalyticsEventNames.PRACTICE_STARTED, emptyMap())

        assertEquals(
            listOf(
                "fullyInitialize",
                "activateApp",
                "newLogger",
                "logEvent:${AnalyticsEventNames.PRACTICE_STARTED}",
            ),
            bootstrap.calls,
        )
    }

    @Test
    fun activateApp_calledOnce_secondCustomEventDoesNotActivateAgain() {
        val bootstrap = RecordingBootstrap()
        val client = DefaultMetaAppEventsClient(
            context = RuntimeEnvironment.getApplication(),
            sdk = bootstrap,
        )

        client.logEvent(AnalyticsEventNames.PRACTICE_STARTED, emptyMap())
        client.logEvent(AnalyticsEventNames.ANSWER_SAVED, emptyMap())

        assertEquals(1, bootstrap.calls.count { it == "fullyInitialize" })
        assertEquals(1, bootstrap.calls.count { it == "activateApp" })
        assertEquals(1, bootstrap.loggerCreations)
        assertEquals(
            listOf(
                "fullyInitialize",
                "activateApp",
                "newLogger",
                "logEvent:${AnalyticsEventNames.PRACTICE_STARTED}",
                "logEvent:${AnalyticsEventNames.ANSWER_SAVED}",
            ),
            bootstrap.calls,
        )
    }

    @Test
    fun realBootstrap_keepsPermanentInitAndForbiddenBehaviors() {
        val source = File("src/main/java/com/me4hik/praktika/measurement/MetaAppEventsClient.kt").readText()
        assertTrue(source.contains("sdk.fullyInitialize()"))
        assertTrue(source.contains("sdk.activateApp(application)"))
        assertFalse(source.contains("setIsDebugEnabled"))
        assertFalse(source.contains("registerAccessToken"))
        assertFalse(source.contains("LoggingBehavior"))
        assertFalse(source.contains("setAutoInitEnabled(true)"))
        assertFalse(source.contains("setAutoLogAppEventsEnabled(true)"))
        assertFalse(source.contains("setAdvertiserIDCollectionEnabled(true)"))
        assertFalse(source.contains(".flush("))
        assertFalse(source.contains("Log.d("))
        assertFalse(source.contains("Log.i("))
        assertFalse(source.contains("Log.w("))
        assertFalse(source.contains("Log.e("))
        assertFalse(source.contains("println("))
    }
}
