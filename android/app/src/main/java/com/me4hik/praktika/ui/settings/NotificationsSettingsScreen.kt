package com.me4hik.praktika.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeGlassActionRow
import com.me4hik.praktika.ui.components.PracticeSectionHeader
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.tour.LocalTourController
import com.me4hik.praktika.ui.tour.TourTargetId
import com.me4hik.praktika.ui.tour.notifyActivation
import com.me4hik.praktika.ui.tour.tourTarget
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.TextMuted
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TextSecondary
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun NotificationsSettingsScreen(
    uiState: SettingsUiState,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onDeferDurationMinutesChanged: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenSoundLibrary: () -> Unit,
    onBack: () -> Unit,
) {
    when (uiState) {
        SettingsUiState.Loading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        is SettingsUiState.FatalError -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_fatal_error),
                    style = MaterialTheme.typography.bodyLarge,
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.practice_back),
                    )
                }
            }
        }

        is SettingsUiState.Content -> {
            NotificationsSettingsContent(
                content = uiState,
                onSoundEnabledChanged = onSoundEnabledChanged,
                onDeferDurationMinutesChanged = onDeferDurationMinutesChanged,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenSoundLibrary = onOpenSoundLibrary,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun NotificationsSettingsContent(
    content: SettingsUiState.Content,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onDeferDurationMinutesChanged: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenSoundLibrary: () -> Unit,
    onBack: () -> Unit,
) {
    val tourController = LocalTourController.current
    Box(modifier = Modifier.fillMaxSize()) {
        PracticeBackground(style = PracticeBackgroundStyle.Subdued)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .testTag(SettingsTestTags.NOTIFICATIONS_SETTINGS_SCREEN),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag(SettingsTestTags.NOTIFICATIONS_SETTINGS_BACK),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.practice_back),
                        tint = TextSecondary,
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_notifications_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PracticeSectionHeader(
                    title = stringResource(R.string.settings_sound_section),
                    icon = Icons.Outlined.VolumeUp,
                )
                PracticeSurface {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourTarget(TourTargetId.NOTIFICATIONS_SOUND_SWITCH),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_sound_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                        )
                        Switch(
                            checked = content.soundEnabled,
                            onCheckedChange = { enabled ->
                                if (tourController?.session?.value?.isActive != true) {
                                    onSoundEnabledChanged(enabled)
                                }
                            },
                            enabled = !content.isChangingSound,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_SOUND_SWITCH),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = AccentViolet.copy(alpha = 0.55f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = TextMuted.copy(alpha = 0.35f),
                            ),
                        )
                    }
                    val selectedAsset = com.me4hik.praktika.sound.BuiltinSoundCatalog
                        .resolveOrDefault(content.selectedSoundId)
                    val selectedDisplayName = com.me4hik.praktika.sound.SoundDisplayNames
                        .forAsset(LocalContext.current, selectedAsset)
                    PracticeGlassActionRow(
                        title = stringResource(R.string.sound_library_entry_title),
                        supportingText = selectedDisplayName,
                        onClick = onOpenSoundLibrary,
                        modifier = Modifier
                            .testTag(SettingsTestTags.SOUND_LIBRARY_ENTRY)
                            .tourTarget(TourTargetId.NOTIFICATIONS_SOUND_LIBRARY),
                    )
                }
                content.soundError?.let {
                    Text(
                        text = stringResource(R.string.settings_sound_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .testTag(SettingsTestTags.SETTINGS_DEFER_SECTION)
                    .tourTarget(TourTargetId.NOTIFICATIONS_DEFER),
            ) {
                PracticeSectionHeader(
                    title = stringResource(R.string.settings_defer_section),
                    icon = Icons.Outlined.Timer,
                )
                PracticeSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val tourController = LocalTourController.current
                        listOf(
                            Triple(5, R.string.settings_defer_5, SettingsTestTags.SETTINGS_DEFER_5),
                            Triple(10, R.string.settings_defer_10, SettingsTestTags.SETTINGS_DEFER_10),
                            Triple(15, R.string.settings_defer_15, SettingsTestTags.SETTINGS_DEFER_15),
                            Triple(30, R.string.settings_defer_30, SettingsTestTags.SETTINGS_DEFER_30),
                        ).forEach { (minutes, labelRes, tag) ->
                            val selected = content.deferDurationMinutes == minutes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        enabled = !content.isChangingDeferDuration &&
                                            minutes in DeferDurationOptions.ALLOWED_MINUTES,
                                        role = Role.RadioButton,
                                        onClick = {
                                            onDeferDurationMinutesChanged(minutes)
                                            tourController.notifyActivation(TourTargetId.NOTIFICATIONS_DEFER)
                                        },
                                    )
                                    .padding(vertical = 4.dp)
                                    .testTag(tag),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                    enabled = !content.isChangingDeferDuration,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AccentViolet,
                                        unselectedColor = TextMuted,
                                    ),
                                )
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
                content.deferError?.let {
                    Text(
                        text = stringResource(R.string.settings_defer_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            PracticeSurface {
                PracticeGlassActionRow(
                    title = stringResource(R.string.settings_notification_settings),
                    icon = Icons.Outlined.Notifications,
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier
                        .testTag(SettingsTestTags.SETTINGS_NOTIFICATION_SETTINGS)
                        .tourTarget(TourTargetId.NOTIFICATIONS_SYSTEM_SETTINGS),
                )
            }
        }
    }
}
