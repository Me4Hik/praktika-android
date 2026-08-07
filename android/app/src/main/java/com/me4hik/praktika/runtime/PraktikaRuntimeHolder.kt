// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - singleton runtime holder
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - application initialize
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.data.local.PraktikaDatabase

object PraktikaRuntimeHolder {
    @Volatile
    private var runtime: PraktikaRuntime? = null

    private val lock = Any()

    fun initialize(context: Context) {
        get(context)
    }

    fun get(context: Context): PraktikaRuntime {
        val existing = runtime
        if (existing != null) {
            return existing
        }
        return synchronized(lock) {
            runtime ?: RuntimeFactory.create(context.applicationContext).also { runtime = it }
        }
    }

    fun isInitialized(): Boolean = runtime != null

    internal fun resetForTests() {
        synchronized(lock) {
            runtime?.initializer?.resetForTests()
            runtime?.database?.close()
            PraktikaDatabase.resetInstanceForTests()
            runtime = null
        }
    }

    // 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - inject isolated runtime for receiver tests
    internal fun setForTests(runtime: PraktikaRuntime) {
        synchronized(lock) {
            this.runtime = runtime
        }
    }
    // 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
