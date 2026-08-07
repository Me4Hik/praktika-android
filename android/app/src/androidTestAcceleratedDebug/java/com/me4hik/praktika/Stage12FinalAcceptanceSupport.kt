// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - final acceptance helpers
package com.me4hik.praktika

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import java.io.File
import org.json.JSONObject
import org.junit.Assume.assumeTrue

object Stage12FinalAcceptanceSupport {
    const val OPT_IN_ARGUMENT = "stage12_final_acceptance"
    const val REBOOT_STATE_FILE = "stage12_final_reboot_state.json"

    fun isOptIn(): Boolean {
        return InstrumentationRegistry.getArguments()
            .getString(OPT_IN_ARGUMENT)
            .toBoolean()
    }

    fun assumeOptIn() {
        assumeTrue(
            "Stage 12 final acceptance runs only through scripts/stage12_final_acceptance.ps1",
            isOptIn(),
        )
    }

    fun resetAcceleratedSandbox(context: Context) {
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter {
                it.name == AcceleratedTimeStorage.STATE_FILE_NAME ||
                    it.name == REBOOT_STATE_FILE ||
                    it.name == Stage12NotificationTapProofSupport.SETUP_STATE_FILE_NAME
            }
            ?.forEach { it.delete() }
        context.getDatabasePath(com.me4hik.praktika.runtime.RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }

    fun tapDenyNotificationPermissionDialog() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        val deadline = System.currentTimeMillis() + 15_000
        var deny = device.findObject(By.text("Don't allow"))
            ?: device.findObject(By.text("Don\u2019t allow"))
            ?: device.findObject(By.text("Запретить"))
        while (deny == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            device.waitForIdle()
            deny = device.findObject(By.text("Don't allow"))
                ?: device.findObject(By.text("Don\u2019t allow"))
                ?: device.findObject(By.text("Запретить"))
        }
        checkNotNull(deny) { "System Deny button not found" }
        deny.click()
        device.waitForIdle()
    }

    fun writeRebootState(context: Context, occurrenceId: Long, cycleNumber: Int) {
        val json = JSONObject()
        json.put("occurrenceId", occurrenceId)
        json.put("cycleNumber", cycleNumber)
        File(context.filesDir, REBOOT_STATE_FILE).writeText(json.toString())
    }

    fun readRebootState(context: Context): Pair<Long, Int> {
        val json = JSONObject(File(context.filesDir, REBOOT_STATE_FILE).readText())
        return json.getLong("occurrenceId") to json.getInt("cycleNumber")
    }
}
// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END
