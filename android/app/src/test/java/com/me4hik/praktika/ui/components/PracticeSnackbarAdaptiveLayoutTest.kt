// 03.10.2026 Snackbar compact layout cursor by Me4Hik START - geometry budget unit coverage
package com.me4hik.praktika.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSnackbarAdaptiveLayoutTest {

    @Test
    fun infoOnly_neverStacks() {
        assertFalse(
            PracticeSnackbarAdaptiveLayout.shouldStackAction(
                hostMaxWidth = 300.dp,
                hasAction = false,
                hasDismiss = false,
            ),
        )
    }

    @Test
    fun actionWithDismiss_stacksWhenInnerContentBelowBudget() {
        // host 360dp → after 12dp*2 outer still measured here as hostMaxWidth in BoxWithConstraints
        // after outer padding; use 336dp (360-24). Inner content = 336-32 = 304 < 356 budget → stack.
        assertTrue(
            PracticeSnackbarAdaptiveLayout.shouldStackAction(
                hostMaxWidth = 336.dp,
                hasAction = true,
                hasDismiss = true,
            ),
        )
    }

    @Test
    fun actionWithDismiss_staysHorizontalWhenWideEnough() {
        // host ~576dp (600-24): inner 544 >= 356 → row
        assertFalse(
            PracticeSnackbarAdaptiveLayout.shouldStackAction(
                hostMaxWidth = 576.dp,
                hasAction = true,
                hasDismiss = true,
            ),
        )
    }

    @Test
    fun budgetMatchesDocumentedGeometry() {
        val budget =
            PracticeSnackbarAdaptiveLayout.MinReadableMessageWidth +
                PracticeSnackbarAdaptiveLayout.ItemGap +
                PracticeSnackbarAdaptiveLayout.EstimatedLongActionWidth +
                PracticeSnackbarAdaptiveLayout.ItemGap +
                PracticeSnackbarAdaptiveLayout.DismissActionWidth
        // 120 + 8 + 172 + 8 + 48 = 356dp
        assertTrue(budget == 356.dp)
        // Just below budget after inner padding → stack
        assertTrue(
            PracticeSnackbarAdaptiveLayout.shouldStackAction(
                hostMaxWidth = budget + PracticeSnackbarAdaptiveLayout.InnerHorizontalPadding - 1.dp,
                hasAction = true,
                hasDismiss = true,
            ),
        )
        // Exactly at budget → horizontal
        assertFalse(
            PracticeSnackbarAdaptiveLayout.shouldStackAction(
                hostMaxWidth = budget + PracticeSnackbarAdaptiveLayout.InnerHorizontalPadding,
                hasAction = true,
                hasDismiss = true,
            ),
        )
    }
}
// 03.10.2026 Snackbar compact layout cursor by Me4Hik END
