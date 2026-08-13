// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - accelerated device acceptance harness Activity
package com.me4hik.praktika.ui.acceptance

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Accelerated-only exported harness. Invoke with symbolic --es action ACTION_NAME.
 * Never accepts URI/path extras. Never mutates production BackupWriteState auth fields.
 */
class DeviceAcceptanceHarnessActivity : ComponentActivity() {

    private lateinit var controller: DeviceAcceptanceHarnessController
    private lateinit var reportStore: DeviceAcceptanceReportStore
    private lateinit var statusView: TextView

    private val temporaryPickerLauncher = registerForActivityResult(
        DeviceAcceptanceOpenDocumentTreeReadOnlyContract(),
    ) { uri ->
        lifecycleScope.launch {
            val report = if (uri == null) {
                controller.cancelledPickerReport()
            } else {
                // Temporary grant only — never persist URI permission / tree hint / authorize.
                controller.inspectTemporaryTreeUri(uri)
            }
            finishWithReport(report)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).also { view ->
            view.text = "DeviceAcceptanceHarness"
            setContentView(view)
        }

        // Ensure accelerated RuntimeFactory has bound the authoritative write-state repository.
        val runtime = PraktikaRuntimeHolder.get(this)

        reportStore = DeviceAcceptanceReportStore(applicationContext)
        controller = DeviceAcceptanceHarnessController(
            uriStash = DeviceAcceptanceUriStash(applicationContext),
            treeReader = DeviceAcceptanceSafTreeReader(contentResolver),
            grantRevoker = DeviceAcceptanceGrantRevoker(contentResolver),
            // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - bind runtime Room exporter reader
            roomStateReader = ExporterDeviceAcceptanceRoomStateReader(runtime.database),
            // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
        )

        val rawAction = intent?.getStringExtra(DeviceAcceptanceHarnessAction.INTENT_EXTRA_ACTION)
        val action = DeviceAcceptanceHarnessAction.parseSymbolic(rawAction)
        if (action == null) {
            finishWithReport(controller.unknownActionReport(rawAction))
            return
        }

        lifecycleScope.launch {
            try {
                when (val result = controller.execute(action)) {
                    is DeviceAcceptanceControllerResult.Completed -> finishWithReport(result.report)
                    DeviceAcceptanceControllerResult.NeedsTemporaryPicker -> {
                        statusView.text = "PICK_AND_INSPECT_ONCE: select folder"
                        temporaryPickerLauncher.launch(null)
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                finishWithReport(
                    DeviceAcceptanceEvidenceReport(
                        action = action.name,
                        resultStatus = DeviceAcceptanceResultStatus.FAILED,
                        harnessExecutedAtEpochMillis = System.currentTimeMillis(),
                        slotA = null,
                        slotB = null,
                        latestValidSlot = null,
                        latestValidSequence = null,
                        latestValidSemantics = null,
                        folderFingerprintSha256 = null,
                    ),
                )
            }
        }
    }

    private fun finishWithReport(report: DeviceAcceptanceEvidenceReport) {
        val fileName = reportStore.writeLatest(report)
        DeviceAcceptanceHarnessLogger.actionCompleted(
            action = report.action,
            resultStatus = report.resultStatus.name,
            reportFileName = fileName,
        )
        statusView.text = "${report.action}:${report.resultStatus.name}"
        finish()
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
