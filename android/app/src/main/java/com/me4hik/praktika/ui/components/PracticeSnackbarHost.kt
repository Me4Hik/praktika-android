// 09.08.2026 Post-release fixes cursor by Me4Hik START - dark Snackbar host
// 09.08.2026 Post-release fixes cursor by Me4Hik START - Snackbar border-to-fill geometry fix
// 03.10.2026 Snackbar compact layout cursor by Me4Hik START - adaptive Row vs action-on-new-line
package com.me4hik.praktika.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.CtaBorderBlue
import com.me4hik.praktika.ui.theme.CtaBorderLavender
import com.me4hik.praktika.ui.theme.ElevatedSurface
import com.me4hik.praktika.ui.theme.GlowViolet
import com.me4hik.praktika.ui.theme.TextPrimary

object PracticeSnackbarTestTags {
    const val CONTAINER = "practice_snackbar_container"
    const val MESSAGE = "practice_snackbar_message"
    const val ACTION = "practice_snackbar_action"
    const val ACTION_ZONE = "practice_snackbar_action_zone"
    const val DISMISS = "practice_snackbar_dismiss"
    const val CHECK = "practice_snackbar_check"
}

/**
 * Geometry budget for keeping message + long action + dismiss on one row.
 *
 * Derived from host chrome and measured content:
 * - min readable single-line message band (~"Ответ сохранён") ≈ 120dp
 * - long action TextButton (~"Посмотреть историю") ≈ 172dp
 * - dismiss IconButton = 48dp
 * - Row gaps = 8dp between items
 *
 * If inner content width is below this sum, message is squeezed into a narrow
 * column and Russian words break mid-syllable — switch to compact one-row layout
 * (short action label, check icon, no dismiss).
 */
internal object PracticeSnackbarAdaptiveLayout {
    val MinReadableMessageWidth: Dp = 120.dp
    val EstimatedLongActionWidth: Dp = 172.dp
    val DismissActionWidth: Dp = 48.dp
    val ItemGap: Dp = 8.dp
    val InnerHorizontalPadding: Dp = 32.dp // 16dp start + 16dp end inside the card

    fun shouldStackAction(
        hostMaxWidth: Dp,
        hasAction: Boolean,
        hasDismiss: Boolean,
    ): Boolean {
        if (!hasAction) return false
        val contentMaxWidth = hostMaxWidth - InnerHorizontalPadding
        var required = MinReadableMessageWidth + ItemGap + EstimatedLongActionWidth
        if (hasDismiss) {
            required += ItemGap + DismissActionWidth
        }
        return contentMaxWidth < required
    }
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val hasAction = snackbarData.visuals.actionLabel != null
            val hasDismiss = snackbarData.visuals.withDismissAction
            val stackAction = PracticeSnackbarAdaptiveLayout.shouldStackAction(
                hostMaxWidth = maxWidth,
                hasAction = hasAction,
                hasDismiss = hasDismiss,
            )
            // Shared chrome; vertical inner padding is compact-only tighter (6.dp vs 14.dp).
            val surfaceChrome = Modifier
                .fillMaxWidth()
                .shadow(elevation = 6.dp, shape = shape, clip = false)
                .clip(shape)
                .background(containerColor)
                .border(width = 1.dp, brush = borderBrush, shape = shape)

            if (stackAction) {
                CompactOneRowPracticeSnackbarContent(
                    snackbarData = snackbarData,
                    actionColor = actionColor,
                    modifier = surfaceChrome
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag(PracticeSnackbarTestTags.CONTAINER),
                )
            } else {
                HorizontalPracticeSnackbarContent(
                    snackbarData = snackbarData,
                    actionColor = actionColor,
                    modifier = surfaceChrome
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag(PracticeSnackbarTestTags.CONTAINER),
                )
            }
        }
    }
}

@Composable
private fun HorizontalPracticeSnackbarContent(
    snackbarData: SnackbarData,
    actionColor: Color,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PracticeSnackbarAdaptiveLayout.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = snackbarData.visuals.message,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag(PracticeSnackbarTestTags.MESSAGE),
        )
        PracticeSnackbarActionRow(
            snackbarData = snackbarData,
            actionColor = actionColor,
        )
    }
}

/**
 * Compact one-row: check + status (start) | short action (end). No dismiss ×, no action-zone.
 */
@Composable
private fun CompactOneRowPracticeSnackbarContent(
    snackbarData: SnackbarData,
    actionColor: Color,
    modifier: Modifier,
) {
    // 03.10.2026 Snackbar one-row compact cursor by Me4Hik START
    val fullActionLabel = snackbarData.visuals.actionLabel
    val compactActionLabel = when (val visuals = snackbarData.visuals) {
        is PracticeActionSnackbarVisuals -> visuals.compactActionLabel
        else -> fullActionLabel
    }
    // Baseline-align status message with action text; CenterVertically alone is insufficient
    // when bodyLarge/labelLarge lineHeights differ and action has a 48dp hit target.
    Row(
        modifier = modifier
            .heightIn(min = PracticeSnackbarAdaptiveLayout.DismissActionWidth),
        horizontalArrangement = Arrangement.spacedBy(PracticeSnackbarAdaptiveLayout.ItemGap),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = AccentViolet.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(18.dp)
                    .testTag(PracticeSnackbarTestTags.CHECK),
            )
            Text(
                text = snackbarData.visuals.message,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(PracticeSnackbarTestTags.MESSAGE),
            )
        }
        if (compactActionLabel != null && fullActionLabel != null) {
            // ≥48.dp hit target with label centered inside so baseline-aligned
            // message/check sit optically in the middle of the card (not top-heavy).
            Box(
                modifier = Modifier
                    .alignByBaseline()
                    .heightIn(min = PracticeSnackbarAdaptiveLayout.DismissActionWidth)
                    .clickable(
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = TextPrimary.copy(alpha = 0.08f)),
                        onClick = { snackbarData.performAction() },
                    )
                    .padding(horizontal = 8.dp)
                    .semantics {
                        contentDescription = fullActionLabel
                        role = Role.Button
                    }
                    .testTag(PracticeSnackbarTestTags.ACTION),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = compactActionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = actionColor,
                    maxLines = 1,
                )
            }
        }
    }
    // 03.10.2026 Snackbar one-row compact cursor by Me4Hik END
}

@Composable
private fun PracticeSnackbarActionRow(
    snackbarData: SnackbarData,
    actionColor: Color,
) {
    snackbarData.visuals.actionLabel?.let { actionLabel ->
        TextButton(
            colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
            onClick = { snackbarData.performAction() },
            modifier = Modifier.testTag(PracticeSnackbarTestTags.ACTION),
            content = { Text(actionLabel) },
        )
    }
    if (snackbarData.visuals.withDismissAction) {
        PracticeSnackbarDismissButton(onClick = { snackbarData.dismiss() })
    }
}

@Composable
private fun PracticeSnackbarDismissButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag(PracticeSnackbarTestTags.DISMISS),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = TextPrimary.copy(alpha = 0.85f),
        )
    }
}
// 03.10.2026 Snackbar compact layout cursor by Me4Hik END
// 09.08.2026 Post-release fixes cursor by Me4Hik END
// 09.08.2026 Post-release fixes cursor by Me4Hik END
