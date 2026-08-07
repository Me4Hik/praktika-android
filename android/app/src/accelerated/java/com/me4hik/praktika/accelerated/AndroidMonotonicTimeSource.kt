// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - Android monotonic time source
package com.me4hik.praktika.accelerated

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.util.TimeZone

class AndroidMonotonicTimeSource(
    private val context: Context,
) : MonotonicTimeSource {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun wallClockEpochMillis(): Long = System.currentTimeMillis()

    override fun bootCount(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (exception: Exception) {
            Log.w(TAG, "BOOT_COUNT unavailable: ${exception.message}")
            BOOT_COUNT_UNAVAILABLE
        }
    }

    override fun currentZoneId(): String = TimeZone.getDefault().id

    companion object {
        const val BOOT_COUNT_UNAVAILABLE = -1
        private const val TAG = "AcceleratedClock"
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
