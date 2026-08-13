// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - application-scoped runtime
// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika

import android.app.Application
import com.me4hik.praktika.diagnostics.DiagnosticBootstrap
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder

class PraktikaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticBootstrap.initialize(this)
        PraktikaRuntimeHolder.initialize(this)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
