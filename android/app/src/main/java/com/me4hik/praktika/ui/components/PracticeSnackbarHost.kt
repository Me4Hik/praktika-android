// 09.08.2026 Post-release fixes cursor by Me4Hik START - dark Snackbar host
// 09.08.2026 Post-release fixes cursor by Me4Hik START - Snackbar border-to-fill geometry fix
package com.me4hik.praktika.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.CtaBorderBlue
import com.me4hik.praktika.ui.theme.CtaBorderLavender
import com.me4hik.praktika.ui.theme.ElevatedSurface
import com.me4hik.praktika.ui.theme.GlowViolet
import com.me4hik.praktika.ui.theme.TextPrimary

object PracticeSnackbarTestTags {
    const val CONTAINER = "practice_snackbar_container"
}

@Composable
fun PracticeSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val borderBrush = Brush.horizontalGradient(
        colors = listOf(
            CtaBorderLavender.copy(alpha = 0.35f),
            GlowViolet.copy(alpha = 0.22f),
            CtaBorderBlue.copy(alpha = 0.30f),
        ),
    )
    val actionColor = AccentViolet.copy(alpha = 0.92f)
    val containerColor = ElevatedSurface.copy(alpha = 0.96f)

    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { snackbarData ->
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shadow(elevation = 6.dp, shape = shape, clip = false)
                .clip(shape)
                .background(containerColor)
                .border(width = 1.dp, brush = borderBrush, shape = shape)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth()
                .testTag(PracticeSnackbarTestTags.CONTAINER),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = snackbarData.visuals.message,
                color = TextPrimary,
                modifier = Modifier.weight(1f, fill = false),
            )
            snackbarData.visuals.actionLabel?.let { actionLabel ->
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
                    onClick = { snackbarData.performAction() },
                    content = { Text(actionLabel) },
                )
            }
            if (snackbarData.visuals.withDismissAction) {
                IconButton(onClick = { snackbarData.dismiss() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
// 09.08.2026 Post-release fixes cursor by Me4Hik END
// 09.08.2026 Post-release fixes cursor by Me4Hik END
