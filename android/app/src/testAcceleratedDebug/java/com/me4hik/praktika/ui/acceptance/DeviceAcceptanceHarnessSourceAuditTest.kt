// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - pick-once / revoke source audit host tests
package com.me4hik.praktika.ui.acceptance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeviceAcceptanceHarnessSourceAuditTest {
    @Test
    fun pickOnceAndRevokeSources_forbidPersistAndStateMutationApis() {
        val root = File("src/accelerated/java/com/me4hik/praktika/ui/acceptance")
        assertTrue(root.isDirectory)
        val sources = root.listFiles { f -> f.extension == "kt" }!!.associate { it.name to it.readText() }

        val pickOnceRelated = listOf(
            "DeviceAcceptanceHarnessActivity.kt",
            "DeviceAcceptanceHarnessController.kt",
            "DeviceAcceptanceOpenDocumentTreeReadOnlyContract.kt",
            "DeviceAcceptanceSafTreeReader.kt",
            "DeviceAcceptanceRoomStateReader.kt",
        )
        for (name in pickOnceRelated) {
            val text = sources.getValue(name)
            assertFalse("$name must not takePersistable", text.contains("takePersistableUriPermission"))
            assertFalse("$name must not saveTreeUri", text.contains("saveTreeUri"))
            assertFalse("$name must not commitAuthorized", text.contains("commitAuthorizedFolderAfterVerifiedWrite"))
            assertFalse("$name must not authorize hint", text.contains("authorizeCurrentTreeUriHint"))
            assertFalse("$name must not backupNow", text.contains("backupNow("))
            assertFalse("$name must not requestBackup", text.contains("requestBackup("))
            assertFalse("$name must not writeSlot", text.contains("writeSlot("))
            assertFalse("$name must not createDocument", text.contains("createDocument("))
            assertFalse("$name must not deleteDocument", text.contains("deleteDocument("))
        }

        val roomReader = sources.getValue("DeviceAcceptanceRoomStateReader.kt")
        assertFalse(roomReader.contains("ContentResolver"))
        assertFalse(roomReader.contains("releasePersistableUriPermission"))
        assertFalse(roomReader.contains("syncEnvironmentAndReconcile"))
        assertTrue(roomReader.contains("RoomBackupExporter"))

        val contract = sources.getValue("DeviceAcceptanceOpenDocumentTreeReadOnlyContract.kt")
        assertTrue(contract.contains("FLAG_GRANT_READ_URI_PERMISSION"))
        assertFalse(contract.contains("FLAG_GRANT_WRITE_URI_PERMISSION"))
        assertFalse(contract.contains("FLAG_GRANT_PERSISTABLE_URI_PERMISSION"))

        val revoker = sources.getValue("DeviceAcceptanceGrantRevoker.kt")
        assertFalse(revoker.contains("markNeedsReconnect"))
        assertFalse(revoker.contains("disableAutomaticBackup"))
        assertFalse(revoker.contains("FLAG_GRANT_PERSISTABLE_URI_PERMISSION"))
        assertTrue(revoker.contains("FLAG_GRANT_READ_URI_PERMISSION"))
        assertTrue(revoker.contains("FLAG_GRANT_WRITE_URI_PERMISSION"))
    }

    @Test
    fun acceleratedManifest_containsHarness_activity() {
        val manifest = File("src/accelerated/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".ui.acceptance.DeviceAcceptanceHarnessActivity"))
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
