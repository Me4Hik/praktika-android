package com.me4hik.praktika.ui.restore

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.me4hik.praktika.ui.theme.PraktikaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductionRestoreSessionHostComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sessionInactive_overlayAbsent() {
        val viewModel = createViewModel()
        composeRule.setContent {
            PraktikaTheme {
                ProductionRestoreSessionHost(viewModel = viewModel)
            }
        }
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_OVERLAY).assertDoesNotExist()
    }

    @Test
    fun sessionActive_overlayPresent() {
        val viewModel = createViewModel()
        composeRule.setContent {
            PraktikaTheme {
                ProductionRestoreSessionHost(viewModel = viewModel)
            }
        }
        composeRule.runOnIdle {
            viewModel.onOpenRestore()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.sessionActive.value
        }
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_OVERLAY).assertExists()
    }

    private fun createViewModel(): ProductionRestoreViewModel {
        val gateway = object : ProductionRestoreGateway {
            override suspend fun checkTargetEligibility(): com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility {
                return com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility.RestoreAvailable
            }

            override suspend fun readTreeUriHint(): String? = null

            override suspend fun readActiveTreeUri(): String? = null

            override suspend fun clearTreeUri() = Unit

            override suspend fun connectFolder(
                uri: android.net.Uri,
                grantFlags: Int,
            ): ProductionRestoreConnectOutcome {
                error("unused in host test")
            }

            override suspend fun connectAndInspectCandidate(
                uri: android.net.Uri,
                grantFlags: Int,
            ): ProductionRestoreCandidateIoResult {
                error("unused in host test")
            }

            override suspend fun commitActiveBackupFolder(uri: android.net.Uri): Boolean = true

            override suspend fun releaseRejectedTransientGrant(
                uri: android.net.Uri,
                grantFlags: Int,
            ) = Unit

            override suspend fun inspectLatest(
                storage: com.me4hik.praktika.data.backup.storage.BackupStorageProvider,
            ): com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult {
                error("unused in host test")
            }

            override suspend fun executeRestore(
                storage: com.me4hik.praktika.data.backup.storage.BackupStorageProvider,
                previewIdentity: com.me4hik.praktika.data.backup.restore.BackupRestoreSelectedIdentity,
                preview: com.me4hik.praktika.data.backup.restore.BackupRestorePreview?,
            ): com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult {
                error("unused in host test")
            }
        }
        return ProductionRestoreViewModel(
            gateway = gateway,
            previewMapper = BackupRestorePreviewMapper(
                dateFormatter = ProductionRestoreDateFormatter(
                    zoneId = java.time.ZoneId.of("UTC"),
                ),
            ),
        )
    }
}
