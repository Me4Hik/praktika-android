package com.me4hik.praktika.ui.restore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionRestoreOverlayPolicyTest {
    @Test
    fun busyStatesRequireIndicator() {
        assertTrue(
            ProductionRestoreOverlayPolicy.showsBusyIndicator(
                ProductionRestoreUiState.CheckingEligibility,
            ),
        )
        assertTrue(
            ProductionRestoreOverlayPolicy.showsBusyIndicator(
                ProductionRestoreUiState.ChoosingFolder,
            ),
        )
        assertTrue(
            ProductionRestoreOverlayPolicy.showsBusyIndicator(
                ProductionRestoreUiState.Connecting,
            ),
        )
        assertTrue(
            ProductionRestoreOverlayPolicy.showsBusyIndicator(
                ProductionRestoreUiState.Inspecting,
            ),
        )
        assertTrue(
            ProductionRestoreOverlayPolicy.showsBusyIndicator(
                ProductionRestoreUiState.Restoring(previewUiModel()),
            ),
        )
        assertFalse(
            ProductionRestoreOverlayPolicy.showsBusyIndicator(
                ProductionRestoreUiState.PreviewReady(
                    preview = previewUiModel(),
                ),
            ),
        )
    }

    @Test
    fun terminalStatesHideCloseAndBlockBackDismiss() {
        val restoring = ProductionRestoreUiState.Restoring(previewUiModel())
        val success = ProductionRestoreUiState.RestoreSuccess(previewUiModel())
        val warning = ProductionRestoreUiState.RuntimeSyncWarning(previewUiModel())

        assertFalse(ProductionRestoreOverlayPolicy.showsCloseIcon(restoring))
        assertFalse(ProductionRestoreOverlayPolicy.showsCloseIcon(success))
        assertFalse(ProductionRestoreOverlayPolicy.showsCloseIcon(warning))

        assertFalse(ProductionRestoreOverlayPolicy.isBackDismissible(restoring))
        assertFalse(ProductionRestoreOverlayPolicy.isBackDismissible(success))
        assertFalse(ProductionRestoreOverlayPolicy.isBackDismissible(warning))
    }

    @Test
    fun dismissibleStatesAllowCloseAndBack() {
        assertTrue(ProductionRestoreOverlayPolicy.showsCloseIcon(ProductionRestoreUiState.Idle))
        assertTrue(ProductionRestoreOverlayPolicy.isBackDismissible(ProductionRestoreUiState.Idle))
        assertTrue(
            ProductionRestoreOverlayPolicy.isBackDismissible(
                ProductionRestoreUiState.PreviewReady(preview = previewUiModel()),
            ),
        )
    }

    private fun previewUiModel(): ProductionRestorePreviewUiModel {
        return ProductionRestorePreviewUiModel(
            createdAtText = "11 августа 2026 · 18:42",
            answerCount = 1,
            completedCount = 1,
            practiceStarted = false,
            deletedTextCount = 0,
        )
    }
}
