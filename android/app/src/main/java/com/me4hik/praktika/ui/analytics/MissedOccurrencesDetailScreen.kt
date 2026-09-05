// PROMPT 157 — reusable missed occurrences detail screen (weekday / question)
package com.me4hik.praktika.ui.analytics

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.MetadataText
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeHeroAccent
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.components.PracticeTopBar
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TitleSerifStyle
import java.time.DayOfWeek

object MissedOccurrencesDetailTestTags {
    const val SCREEN = "analytics_missed_detail_screen"
    const val EMPTY = "analytics_missed_detail_empty"
    const val ERROR = "analytics_missed_detail_error"
    const val RETRY = "analytics_missed_detail_retry"
    const val BACK = "analytics_missed_detail_back"
    const val QUESTION_HEADER = "analytics_missed_detail_question_header"
    const val ROW_PREFIX = "analytics_missed_detail_row_"
}

@Composable
fun MissedOccurrencesDetailScreen(
    uiState: MissedOccurrencesDetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MissedOccurrencesDetailTestTags.SCREEN),
    ) {
        PracticeBackground(style = PracticeBackgroundStyle.Archive)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            PracticeTopBar(
                onBack = onBack,
                backTestTag = MissedOccurrencesDetailTestTags.BACK,
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
            when (uiState) {
                MissedOccurrencesDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = TextPrimary)
                    }
                }
                MissedOccurrencesDetailUiState.Empty -> {
                    Text(
                        text = stringResource(R.string.analytics_missed_detail_empty),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .testTag(MissedOccurrencesDetailTestTags.EMPTY),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                is MissedOccurrencesDetailUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .testTag(MissedOccurrencesDetailTestTags.ERROR),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.analytics_missed_detail_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag(MissedOccurrencesDetailTestTags.RETRY),
                        ) {
                            Text(text = stringResource(R.string.practice_retry))
                        }
                    }
                }
                is MissedOccurrencesDetailUiState.Content -> {
                    DetailContent(
                        content = uiState,
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
private fun DetailContent(
    content: MissedOccurrencesDetailUiState.Content,
    modifier: Modifier = Modifier,
) {
    val title = when (val mode = content.mode) {
        is MissedOccurrencesDetailMode.Weekday -> stringResource(
            R.string.analytics_missed_detail_weekday_title,
            weekdayLabel(mode.dayOfWeek),
        )
        is MissedOccurrencesDetailMode.Question -> stringResource(
            R.string.analytics_missed_detail_question_title,
        )
    }
    val questionHeader = (content.mode as? MissedOccurrencesDetailMode.Question)?.questionText

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") {
            Text(
                text = title,
                style = TitleSerifStyle,
                color = TextQuestionSoft,
            )
        }
        if (questionHeader != null) {
            item(key = "question_header") {
                Text(
                    text = questionHeader,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.testTag(MissedOccurrencesDetailTestTags.QUESTION_HEADER),
                )
            }
        }
        items(content.rows, key = { it.occurrenceId }) { row ->
            PracticeSurface(
                modifier = Modifier.testTag(
                    "${MissedOccurrencesDetailTestTags.ROW_PREFIX}${row.occurrenceId}",
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = row.formattedDateTime,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    val secondary = row.questionText
                    if (secondary != null) {
                        MetadataText(text = secondary)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
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
