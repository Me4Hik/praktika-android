// 03.10.2026 Snackbar one-row compact cursor by Me4Hik START - debug visual harness @360dp
package com.me4hik.praktika.ui.components

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.yield

/**
 * Debug-only harness: shows the real compact action snackbar indefinitely
 * for emulator screenshot review. Does not touch production save-flow.
 */
class PracticeSnackbarVisualHarnessActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PraktikaTheme {
                val hostState = remember { SnackbarHostState() }
                val message = stringResource(R.string.answer_saved_confirmation)
                val actionLabel = stringResource(R.string.answer_view_history_action)
                val compactActionLabel = stringResource(R.string.answer_view_history_action_compact)
                Box(modifier = Modifier.fillMaxSize()) {
                    PracticeSnackbarHost(
                        hostState = hostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                LaunchedEffect(hostState, message, actionLabel, compactActionLabel) {
                    yield()
                    hostState.showSnackbar(
                        PracticeActionSnackbarVisuals(
                            message = message,
                            actionLabel = actionLabel,
                            compactActionLabel = compactActionLabel,
                            duration = SnackbarDuration.Indefinite,
                        ),
                    )
                }
            }
        }
    }
}
// 03.10.2026 Snackbar one-row compact cursor by Me4Hik END
