package com.me4hik.praktika.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.TextMuted
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextSecondary
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun SoundLibraryScreen(
    uiState: SoundLibraryUiState,
    onSelect: (String) -> Unit,
    onTogglePreview: (String) -> Unit,
    onHide: (String) -> Unit,
    onRestoreAllHidden: () -> Unit,
    onStopPreview: () -> Unit,
    onBack: () -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose { onStopPreview() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PracticeBackground(style = PracticeBackgroundStyle.Subdued)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .testTag(SoundLibraryTestTags.SOUND_LIBRARY_SCREEN),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onStopPreview()
                        onBack()
                    },
                    modifier = Modifier.testTag(SoundLibraryTestTags.SOUND_LIBRARY_BACK),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.practice_back),
                        tint = TextPrimary,
                    )
                }
                Text(
                    text = stringResource(R.string.sound_library_title),
                    style = TitleSerifStyle,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Text(
                text = stringResource(
                    R.string.sound_library_currently_selected,
                    uiState.selectedDisplayName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .testTag(SoundLibraryTestTags.SOUND_LIBRARY_SELECTED_LABEL),
            )

            if (!uiState.soundEnabled) {
                Text(
                    text = stringResource(R.string.sound_library_notifications_muted_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (uiState.hiddenCount > 0) {
                TextButton(
                    onClick = onRestoreAllHidden,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .testTag(SoundLibraryTestTags.SOUND_RESTORE_HIDDEN),
                ) {
                    Text(text = stringResource(R.string.sound_library_restore_hidden))
                }
            }

            PracticeSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(SoundLibraryTestTags.SOUND_LIBRARY_LIST),
                ) {
                    items(
                        items = uiState.items,
                        key = { it.asset.id },
                    ) { item ->
                        SoundLibraryRow(
                            item = item,
                            isPreviewing = uiState.currentlyPreviewingId == item.asset.id,
                            onSelect = { onSelect(item.asset.id) },
                            onTogglePreview = { onTogglePreview(item.asset.id) },
                            onHide = { onHide(item.asset.id) },
                        )
                        HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SoundLibraryRow(
    item: SoundLibraryItemUi,
    isPreviewing: Boolean,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit,
    onHide: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedDescription = if (item.selected) {
        stringResource(R.string.sound_library_cd_selected)
    } else {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .selectable(
                selected = item.selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .semantics {
                selected = item.selected
                selectedDescription?.let { contentDescription = it }
            }
            .padding(horizontal = 4.dp)
            .testTag(SoundLibraryTestTags.item(item.asset.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(
            selected = item.selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = AccentViolet,
                unselectedColor = TextMuted,
            ),
        )
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onTogglePreview,
            enabled = item.previewAvailable,
            modifier = Modifier.testTag(SoundLibraryTestTags.play(item.asset.id)),
        ) {
            Icon(
                imageVector = if (isPreviewing) Icons.Outlined.Stop else Icons.Outlined.VolumeUp,
                contentDescription = stringResource(
                    if (isPreviewing) {
                        R.string.sound_library_cd_stop
                    } else {
                        R.string.sound_library_cd_play
                    },
                ),
                tint = if (item.previewAvailable) AccentViolet else TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        if (item.canHide) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag(SoundLibraryTestTags.more(item.asset.id)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.sound_library_cd_more),
                        tint = TextMuted,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.sound_library_hide_action))
                        },
                        onClick = {
                            menuExpanded = false
                            onHide()
                        },
                    )
                }
            }
        }
    }
}
