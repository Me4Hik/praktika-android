// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import android.app.Application
import com.me4hik.praktika.BuildConfig
import io.sentry.android.core.SentryAndroid

object DiagnosticBootstrap {
    fun initialize(application: Application) {
        DiagnosticsRecorder.initialize(application)
        DiagnosticUncaughtExceptionHandler.install()
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.APP,
            name = "process_start",
            metadata = mapOf(
                "version_code" to BuildConfig.VERSION_CODE.toString(),
                "version_name" to BuildConfig.VERSION_NAME,
                "flavor" to BuildConfig.FLAVOR,
                "package" to application.packageName,
            ),
        )
        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryAndroid.init(application) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.environment = BuildConfig.FLAVOR
                options.release = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                options.isEnableAutoSessionTracking = false
            }
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
