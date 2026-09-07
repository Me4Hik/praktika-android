// PROMPT 167 — Archive root hub (no date list)
// PROMPT 170 — export/share accordion on hub
package com.me4hik.praktika.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeGlassActionRow
import com.me4hik.praktika.ui.components.PracticeHeroAccent
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.components.PracticeTopBar
import com.me4hik.praktika.ui.tour.LocalTourController
import com.me4hik.praktika.ui.tour.TourSessionState
import com.me4hik.praktika.ui.tour.TourTargetId
import com.me4hik.praktika.ui.tour.notifyActivation
import com.me4hik.praktika.ui.tour.tourTarget
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TitleSerifStyle
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun ArchiveHubScreen(
    onOpenDays: () -> Unit,
    onOpenQuestions: () -> Unit,
    onOpenInsights: () -> Unit,
    onExportAll: () -> Unit,
    onExportPeriod: () -> Unit,
    onShareAll: () -> Unit,
    onSharePeriod: () -> Unit,
    onBack: () -> Unit,
    // 06.09.2026 Archive period bounds cursor by Me4Hik START - disable period when no answers
    periodSelectionEnabled: Boolean = true,
    // 06.09.2026 Archive period bounds cursor by Me4Hik END
) {
    var exportShareExpanded by rememberSaveable { mutableStateOf(false) }
    val tourController = LocalTourController.current
    val tourSession by (
        tourController?.session
            ?: remember { MutableStateFlow(TourSessionState()) }
        ).collectAsStateWithLifecycle()
    val tourActive = tourController != null && tourSession.isActive

    // After accordion click or Skip: export/share targets must exist immediately.
    LaunchedEffect(tourActive, tourSession.currentStep?.targetId) {
        if (!tourActive) return@LaunchedEffect
        when (tourSession.currentStep?.targetId) {
            TourTargetId.ARCHIVE_EXPORT_SECTION,
            TourTargetId.ARCHIVE_SHARE_SECTION,
            -> exportShareExpanded = true
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ArchiveTestTags.HUB_SCREEN)
            .tourTarget(TourTargetId.ARCHIVE_HUB),
    ) {
        PracticeBackground(style = PracticeBackgroundStyle.Archive)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            PracticeTopBar(
                onBack = onBack,
                backTestTag = ArchiveTestTags.BACK,
                backModifier = Modifier.tourTarget(TourTargetId.ARCHIVE_BACK),
                trailing = {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(56.dp),
                    ) {
                        PracticeHeroAccent(
                            modifier = Modifier.fillMaxSize(),
                            widthFraction = 1f,
                            minHeight = 48.dp,
                            maxHeight = 56.dp,
                            glowAlphaMultiplier = 0.45f,
                            contentAlignment = Alignment.TopEnd,
                            showWarmCore = false,
                        )
                    }
                },
            )
            Text(
                text = stringResource(R.string.archive_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PracticeSurface(contentPadding = PaddingValues(4.dp)) {
                    PracticeGlassActionRow(
                        title = stringResource(R.string.archive_open_days),
                        icon = Icons.Outlined.CalendarMonth,
                        onClick = onOpenDays,
                        modifier = Modifier
                            .testTag(ArchiveTestTags.OPEN_DAYS)
                            .tourTarget(TourTargetId.ARCHIVE_OPEN_DAYS),
                    )
                }
                PracticeSurface(contentPadding = PaddingValues(4.dp)) {
                    PracticeGlassActionRow(
                        title = stringResource(R.string.archive_open_questions),
                        icon = Icons.Outlined.HelpOutline,
                        onClick = onOpenQuestions,
                        modifier = Modifier.testTag(ArchiveTestTags.OPEN_QUESTIONS),
                    )
                }
                PracticeSurface(contentPadding = PaddingValues(4.dp)) {
                    PracticeGlassActionRow(
                        title = stringResource(R.string.archive_open_insights),
                        icon = Icons.Outlined.Insights,
                        onClick = onOpenInsights,
                        modifier = Modifier.testTag(ArchiveTestTags.OPEN_INSIGHTS),
                    )
                }
                PracticeSurface(contentPadding = PaddingValues(4.dp)) {
                    Column {
                        PracticeGlassActionRow(
                            title = stringResource(R.string.archive_export_and_share),
                            icon = if (exportShareExpanded) {
                                Icons.Outlined.ExpandLess
                            } else {
                                Icons.Outlined.ExpandMore
                            },
                            showChevron = false,
                            onClick = {
                                val accordionStep =
                                    tourActive &&
                                        tourSession.currentStep?.targetId ==
                                        TourTargetId.ARCHIVE_EXPORT_ACCORDION
                                if (accordionStep) {
                                    exportShareExpanded = true
                                    tourController.notifyActivation(
                                        TourTargetId.ARCHIVE_EXPORT_ACCORDION,
                                    )
                                } else {
                                    exportShareExpanded = !exportShareExpanded
                                }
                            },
                            modifier = Modifier
                                .testTag(ArchiveTestTags.EXPORT_SHARE_ACCORDION)
                                .tourTarget(TourTargetId.ARCHIVE_EXPORT_ACCORDION),
                        )
                        if (exportShareExpanded) {
                            Column(
                                modifier = Modifier.tourTarget(TourTargetId.ARCHIVE_EXPORT_SECTION),
                            ) {
                                PracticeGlassActionRow(
                                    title = stringResource(R.string.archive_export_all),
                                    icon = Icons.Outlined.FileDownload,
                                    showChevron = false,
                                    onClick = onExportAll,
                                    modifier = Modifier.testTag(ArchiveTestTags.EXPORT_ALL),
                                )
                                PracticeGlassActionRow(
                                    title = stringResource(R.string.archive_export_period),
                                    icon = Icons.Outlined.FileDownload,
                                    showChevron = false,
                                    // 06.09.2026 Archive period bounds cursor by Me4Hik START
                                    enabled = periodSelectionEnabled,
                                    // 06.09.2026 Archive period bounds cursor by Me4Hik END
                                    onClick = onExportPeriod,
                                    modifier = Modifier.testTag(ArchiveTestTags.EXPORT_PERIOD),
                                )
                            }
                            Column(
                                modifier = Modifier.tourTarget(TourTargetId.ARCHIVE_SHARE_SECTION),
                            ) {
                                PracticeGlassActionRow(
                                    title = stringResource(R.string.archive_share_all),
                                    icon = Icons.Outlined.Share,
                                    showChevron = false,
                                    onClick = onShareAll,
                                    modifier = Modifier.testTag(ArchiveTestTags.SHARE_ALL),
                                )
                                PracticeGlassActionRow(
                                    title = stringResource(R.string.archive_share_period),
                                    icon = Icons.Outlined.Share,
                                    showChevron = false,
                                    // 06.09.2026 Archive period bounds cursor by Me4Hik START
                                    enabled = periodSelectionEnabled,
                                    // 06.09.2026 Archive period bounds cursor by Me4Hik END
                                    onClick = onSharePeriod,
                                    modifier = Modifier.testTag(ArchiveTestTags.SHARE_PERIOD),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
