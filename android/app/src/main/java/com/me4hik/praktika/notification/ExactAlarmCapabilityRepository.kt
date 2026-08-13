// 10.08.2026 Post-release fixes cursor by Me4Hik START - exact scheduled reminder delivery
package com.me4hik.praktika.notification

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExactAlarmCapabilityRepository(
    private val context: Context,
) : ExactAlarmCapabilityPolicy {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val _capabilityStateRevision = MutableStateFlow(0L)
    override val capabilityStateRevision: StateFlow<Long> = _capabilityStateRevision.asStateFlow()

    @Volatile
    private var lastKnownCapability: ExactAlarmCapability? = null

    override fun currentCapability(): ExactAlarmCapability {
        return ExactAlarmCapabilityResolver.resolve(alarmManager)
    }

    override fun notifyCapabilityChanged(source: String): Boolean {
        val current = currentCapability()
        TargetedBugDiagnostics.recordExactAlarmCapabilityState(
            capability = ExactAlarmCapabilityResolver.diagnosticLabel(current),
            apiLevel = Build.VERSION.SDK_INT,
            source = source,
        )
        val changed = lastKnownCapability != null && lastKnownCapability != current
        lastKnownCapability = current
        _capabilityStateRevision.value += 1L
        return changed
    }

    override fun seedInitialCapability(source: String) {
        if (lastKnownCapability == null) {
            lastKnownCapability = currentCapability()
            TargetedBugDiagnostics.recordExactAlarmCapabilityState(
                capability = ExactAlarmCapabilityResolver.diagnosticLabel(lastKnownCapability!!),
                apiLevel = Build.VERSION.SDK_INT,
                source = source,
            )
        }
    }

    override fun createRequestExactAlarmIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    }

    override fun recordSettingsCta(source: String) {
        TargetedBugDiagnostics.recordExactAlarmSettingsCta(source)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
