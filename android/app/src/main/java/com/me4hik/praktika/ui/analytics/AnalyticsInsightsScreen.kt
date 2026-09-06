// PROMPT 137 — Analytics Insights Compose screen (Archive-style, no charts)
// PROMPT 157 — weekday accordion + missed drill-down clicks
package com.me4hik.praktika.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.MetadataText
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeHeroAccent
import com.me4hik.praktika.ui.components.PracticeSectionHeader
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.components.PracticeTopBar
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TitleSerifStyle
import java.time.DayOfWeek

object AnalyticsInsightsTestTags {
    const val SCREEN = "analytics_insights_screen"
    const val EMPTY = "analytics_insights_empty"
    const val LIST = "analytics_insights_list"
    const val LOW_SAMPLE_HINT = "analytics_insights_low_sample_hint"
    const val ERROR = "analytics_insights_error"
    const val RETRY = "analytics_insights_retry"
    const val BACK = "analytics_insights_back"
    const val WEEKDAYS_HEADER = "analytics_weekdays_header"
    const val WEEKDAY_ROW_PREFIX = "analytics_weekday_row_"
    const val TOP_MISSED_PREFIX = "analytics_top_missed_"
    const val TOP_DEFERRED_PREFIX = "analytics_top_deferred_"
    const val DURATION = "analytics_duration_section"
}

@Composable
fun AnalyticsInsightsScreen(
    uiState: AnalyticsInsightsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onWeekdayMissedClick: (DayOfWeek) -> Unit = {},
    onTopMissedQuestionClick: (Int) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AnalyticsInsightsTestTags.SCREEN),
    ) {
        PracticeBackground(style = PracticeBackgroundStyle.Archive)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            PracticeTopBar(
                onBack = onBack,
                backTestTag = AnalyticsInsightsTestTags.BACK,
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
                text = stringResource(R.string.analytics_insights_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (uiState) {
                AnalyticsInsightsUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = TextPrimary)
                    }
                }
                AnalyticsInsightsUiState.Empty -> {
                    Text(
                        text = stringResource(R.string.analytics_empty),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .testTag(AnalyticsInsightsTestTags.EMPTY),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                is AnalyticsInsightsUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .testTag(AnalyticsInsightsTestTags.ERROR),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = uiState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag(AnalyticsInsightsTestTags.RETRY),
                        ) {
                            Text(text = stringResource(R.string.practice_retry))
                        }
                    }
                }
                is AnalyticsInsightsUiState.Content -> {
                    InsightsContent(
                        content = uiState,
                        onWeekdayMissedClick = onWeekdayMissedClick,
                        onTopMissedQuestionClick = onTopMissedQuestionClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsContent(
    content: AnalyticsInsightsUiState.Content,
    onWeekdayMissedClick: (DayOfWeek) -> Unit,
    onTopMissedQuestionClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekdays = content.weekdays.sortedBy { it.dayOfWeek.value }
    // 06.09.2026 Archive UX polish cursor by Me4Hik START - weekday accordion collapsed by default
    var weekdaysExpanded by rememberSaveable { mutableStateOf(false) }
    // 06.09.2026 Archive UX polish cursor by Me4Hik END
    LazyColumn(
        modifier = modifier.testTag(AnalyticsInsightsTestTags.LIST),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!content.hasAnyInsightEligible) {
            item(key = "low_sample") {
                PracticeSurface(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_low_sample_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.testTag(AnalyticsInsightsTestTags.LOW_SAMPLE_HINT),
                    )
                }
            }
        }
        if (weekdays.isNotEmpty()) {
            item(key = "weekdays_header") {
                val expandCd = if (weekdaysExpanded) {
                    stringResource(R.string.analytics_weekdays_collapse_cd)
                } else {
                    stringResource(R.string.analytics_weekdays_expand_cd)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AnalyticsInsightsTestTags.WEEKDAYS_HEADER)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = expandCd,
                        ) {
                            weekdaysExpanded = !weekdaysExpanded
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PracticeSectionHeader(
                        title = stringResource(R.string.analytics_section_weekdays),
                        icon = Icons.Outlined.CalendarMonth,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (weekdaysExpanded) {
                            Icons.Outlined.ExpandLess
                        } else {
                            Icons.Outlined.ExpandMore
                        },
                        contentDescription = expandCd,
                        tint = AccentViolet.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (weekdaysExpanded) {
                items(weekdays, key = { it.dayOfWeek }) { day ->
                    val canOpenMissed = day.missed.numerator > 0
                    WeekdayInsightCard(
                        row = day,
                        modifier = Modifier
                            .testTag(
                                "${AnalyticsInsightsTestTags.WEEKDAY_ROW_PREFIX}${day.dayOfWeek.name}",
                            )
                            .then(
                                if (canOpenMissed) {
                                    Modifier.clickable(
                                        role = Role.Button,
                                    ) {
                                        onWeekdayMissedClick(day.dayOfWeek)
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        if (content.topMissed.isNotEmpty()) {
            item(key = "missed_header") {
                PracticeSectionHeader(
                    title = stringResource(R.string.analytics_section_top_missed),
                    icon = Icons.Outlined.HelpOutline,
                )
            }
            items(content.topMissed, key = { "missed_${it.questionId}" }) { question ->
                QuestionInsightCard(
                    questionText = question.questionText,
                    supporting = stringResource(
                        R.string.analytics_missed_of,
                        question.numerator,
                        question.denominator,
                    ),
                    modifier = Modifier
                        .testTag(
                            "${AnalyticsInsightsTestTags.TOP_MISSED_PREFIX}${question.questionId}",
                        )
                        .clickable(role = Role.Button) {
                            onTopMissedQuestionClick(question.questionId)
                        },
                )
            }
        }
        if (content.topDeferred.isNotEmpty()) {
            item(key = "deferred_header") {
                PracticeSectionHeader(
                    title = stringResource(R.string.analytics_section_top_deferred),
                    icon = Icons.Outlined.Schedule,
                )
            }
            items(content.topDeferred, key = { "deferred_${it.questionId}" }) { question ->
                QuestionInsightCard(
                    questionText = question.questionText,
                    supporting = stringResource(
                        R.string.analytics_question_deferred_of,
                        question.numerator,
                        question.denominator,
                    ),
                    modifier = Modifier.testTag(
                        "${AnalyticsInsightsTestTags.TOP_DEFERRED_PREFIX}${question.questionId}",
                    ),
                )
            }
        }
        if (content.duration.totalDeferEvents > 0) {
            item(key = "duration_header") {
                PracticeSectionHeader(
                    title = stringResource(R.string.analytics_section_duration),
                    icon = Icons.Outlined.Timer,
                )
            }
            item(key = "duration_body") {
                val durationText = if (content.duration.eligible && content.duration.modeDurationMinutes != null) {
                    stringResource(
                        R.string.analytics_duration_mode,
                        content.duration.modeDurationMinutes,
                    )
                } else {
                    stringResource(
                        R.string.analytics_duration_low_sample,
                        content.duration.totalDeferEvents,
                    )
                }
                PracticeSurface(
                    modifier = Modifier.testTag(AnalyticsInsightsTestTags.DURATION),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun WeekdayInsightCard(
    row: AnalyticsWeekdayInsightRow,
    modifier: Modifier = Modifier,
) {
    PracticeSurface(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = weekdayLabel(row.dayOfWeek),
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            MetadataText(
                text = stringResource(
                    R.string.analytics_missed_of,
                    row.missed.numerator,
                    row.missed.denominator,
                ),
            )
            MetadataText(
                text = stringResource(
                    R.string.analytics_deferred_of,
                    row.deferred.numerator,
                    row.deferred.denominator,
                ),
            )
            MetadataText(
                text = stringResource(
                    R.string.analytics_rejected_of,
                    row.rejected.numerator,
                    row.rejected.denominator,
                ),
            )
        }
    }
}

@Composable
private fun QuestionInsightCard(
    questionText: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    PracticeSurface(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = questionText,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            MetadataText(text = supporting)
        }
    }
}

@Composable
private fun weekdayLabel(dayOfWeek: DayOfWeek): String {
    val resId = when (dayOfWeek) {
        DayOfWeek.MONDAY -> R.string.analytics_weekday_monday
        DayOfWeek.TUESDAY -> R.string.analytics_weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.analytics_weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.analytics_weekday_thursday
        DayOfWeek.FRIDAY -> R.string.analytics_weekday_friday
        DayOfWeek.SATURDAY -> R.string.analytics_weekday_saturday
        DayOfWeek.SUNDAY -> R.string.analytics_weekday_sunday
    }
    return stringResource(resId)
}
