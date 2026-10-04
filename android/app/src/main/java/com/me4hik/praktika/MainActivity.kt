// 04.08.2026 Reminder App cursor by Me4Hik START - точка входа Activity
// 04.08.2026 Seed Data cursor by Me4Hik START - seed перед setContent
// 04.08.2026 Cycle Engine cursor by Me4Hik START - reconcile после seed
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - PraktikaRuntimeHolder
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - init gate, tap intent, permission
// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticPermissionRecorder
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.NotificationTapDecision
import com.me4hik.praktika.notification.NotificationTapSource
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.PraktikaApp
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Must be [AppCompatActivity] so [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales]
 * can sync to the platform LocaleManager (Compose hosts).
 */
class MainActivity : AppCompatActivity() {

    // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - activity instance marker for onNewIntent proof
    private val activityInstanceId = nextActivityInstanceId.incrementAndGet()
    // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END

    private val postNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - permission result observability
        if (DiagnosticsRecorder.isInitialized()) {
            DiagnosticsRecorder.get().record(
                category = DiagnosticCategory.PERMISSION,
                name = "permission_result",
                metadata = mapOf(
                    "permission" to "POST_NOTIFICATIONS",
                    "granted" to granted.toString(),
                ),
            )
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        lifecycleScope.launch {
            // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik START - keep permission sync off Main
            withContext(Dispatchers.IO) {
                val runtime = PraktikaRuntimeHolder.get(applicationContext)
                runtime.notificationCoordinator.sync(NotificationSyncReason.PERMISSION_CHANGED)
                // 10.08.2026 Post-release fixes cursor by Me4Hik START - permission state after sync
                val soundEnabled = runtime.soundPreferenceRepository.soundEnabled.first()
                DiagnosticPermissionRecorder.recordState(
                    permissionRepository = runtime.notificationPermissionRepository,
                    soundEnabled = soundEnabled,
                    source = "permission_result_after_sync",
                )
                runtime.notificationPermissionRepository.notifyPermissionStateChanged(
                    source = "runtime_permission_callback",
                )
                // 10.08.2026 Post-release fixes cursor by Me4Hik END
            }
            // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik END
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - activity lifecycle diagnostics
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                recordActivityLifecycle("activity_create")
            }

            override fun onStart(owner: LifecycleOwner) {
                recordActivityLifecycle("activity_start")
                TargetedBugDiagnostics.recordAppForegrounded()
            }

            override fun onResume(owner: LifecycleOwner) {
                recordActivityLifecycle("activity_resume")
                if (PraktikaRuntimeHolder.isInitialized()) {
                    val runtime = PraktikaRuntimeHolder.get(applicationContext)
                    runtime.notificationPermissionRepository
                        .notifyPermissionStateChanged(source = "activity_resume")
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            runtime.exactAlarmCapabilityRepository.seedInitialCapability("activity_resume_seed")
                            if (runtime.exactAlarmCapabilityRepository.notifyCapabilityChanged("activity_resume")) {
                                runtime.notificationCoordinator.sync(
                                    NotificationSyncReason.EXACT_ALARM_PERMISSION_CHANGED,
                                )
                            }
                        }
                    }
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                recordActivityLifecycle("activity_pause")
            }

            override fun onStop(owner: LifecycleOwner) {
                recordActivityLifecycle("activity_stop")
                TargetedBugDiagnostics.recordAppBackgrounded()
            }
        })
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - cold-start instance marker
        activeActivityInstanceId = activityInstanceId
        android.util.Log.i(
            NOTIFICATION_TAP_TAG,
            "activityCreated instanceId=$activityInstanceId savedState=${savedInstanceState != null}",
        )
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END

        MainActivityInitGate.beginInit()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MainActivityInitGate.awaitInit()
                try {
                    // 07.08.2026 Stage 26 Release cursor by Me4Hik START - foreground notification sync off Main
                    withContext(Dispatchers.IO) {
                        val runtime = PraktikaRuntimeHolder.get(applicationContext)
                        runtime.foregroundDriver.runWhileForeground()
                    }
                    // 07.08.2026 Stage 26 Release cursor by Me4Hik END
                } finally {
                    withContext(Dispatchers.IO) {
                        if (PraktikaRuntimeHolder.isInitialized()) {
                            PraktikaRuntimeHolder.get(applicationContext)
                                .foregroundDriver
                                .onForegroundStopped()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            try {
                val runtime = withContext(Dispatchers.IO) {
                    val loadedRuntime = PraktikaRuntimeHolder.get(applicationContext)
                    val initialized = loadedRuntime.initializer.ensureInitialized()
                    if (!initialized) {
                        error("Runtime initialization failed")
                    }
                    handleNotificationIntent(loadedRuntime, intent, NotificationTapSource.INITIAL_INTENT)
                    loadedRuntime
                }
                setContent {
                    PraktikaTheme {
                        PraktikaApp(
                            runtime = runtime,
                            activity = this@MainActivity,
                            permissionLauncher = { permission ->
                                postNotificationsPermissionLauncher.launch(permission)
                            },
                        )
                    }
                }
                MainActivityInitGate.completeInit()
            } catch (exception: Exception) {
                Log.e(TAG, "Database initialization failed", exception)
                throw exception
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - onNewIntent delivery marker
        val deliveryCount = onNewIntentDeliveryCount.incrementAndGet()
        android.util.Log.i(
            NOTIFICATION_TAP_TAG,
            "onNewIntent instanceId=$activityInstanceId deliveryCount=$deliveryCount",
        )
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END
        lifecycleScope.launch {
            if (!MainActivityInitGate.isCompleted()) {
                return@launch
            }
            withContext(Dispatchers.IO) {
                handleNotificationIntent(
                    PraktikaRuntimeHolder.get(applicationContext),
                    intent,
                    NotificationTapSource.ON_NEW_INTENT,
                )
            }
        }
    }

    private suspend fun handleNotificationIntent(
        runtime: com.me4hik.praktika.runtime.PraktikaRuntime,
        intent: Intent?,
        source: NotificationTapSource,
    ) {
        if (intent == null) {
            return
        }
        if (intent.getStringExtra(EXTRA_NOTIFICATION_SOURCE) != NOTIFICATION_SOURCE_VALUE) {
            return
        }
        val occurrenceId = intent.getLongExtra(EXTRA_NOTIFICATION_OCCURRENCE_ID, INVALID_ID)
        val plannedAt = intent.getLongExtra(EXTRA_NOTIFICATION_PLANNED_AT, INVALID_ID)
        if (occurrenceId <= 0L || plannedAt <= 0L) {
            return
        }
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - tap intent diagnostic log
        android.util.Log.i(
            NOTIFICATION_TAP_TAG,
            "handleIntent source=$source instanceId=$activityInstanceId occurrenceId=$occurrenceId plannedAt=$plannedAt",
        )
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END
        when (
            runtime.notificationCoordinator.handleNotificationTap(
                occurrenceId = occurrenceId,
                plannedAtEpochMillis = plannedAt,
                tapSource = source,
            )
        ) {
            is NotificationTapDecision.Open -> Unit
            NotificationTapDecision.Ignore -> Unit
        }
    }

    private fun recordActivityLifecycle(name: String) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.APP,
            name = name,
            metadata = mapOf(
                "activity" to "MainActivity",
                "instance_id" to activityInstanceId.toString(),
            ),
        )
    }

    companion object {
        const val TAG = "MainActivity"
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - tap proof markers
        const val NOTIFICATION_TAP_TAG = "NotificationTap"
        private val nextActivityInstanceId = AtomicInteger(0)
        private val onNewIntentDeliveryCount = AtomicInteger(0)

        @Volatile
        var activeActivityInstanceId: Int = -1
            internal set

        fun resetTapProofMarkersForTests() {
            activeActivityInstanceId = -1
            onNewIntentDeliveryCount.set(0)
        }
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END
        const val EXTRA_NOTIFICATION_OCCURRENCE_ID = "notification_occurrence_id"
        const val EXTRA_NOTIFICATION_PLANNED_AT = "notification_planned_at"
        const val EXTRA_NOTIFICATION_SOURCE = "notification_source"
        const val NOTIFICATION_SOURCE_VALUE = "notification"
        private const val INVALID_ID = -1L
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
// 04.08.2026 Cycle Engine cursor by Me4Hik END
// 04.08.2026 Seed Data cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
