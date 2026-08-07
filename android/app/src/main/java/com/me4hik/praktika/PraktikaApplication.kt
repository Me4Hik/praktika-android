// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - application-scoped runtime
package com.me4hik.praktika

import android.app.Application
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder

class PraktikaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PraktikaRuntimeHolder.initialize(this)
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
