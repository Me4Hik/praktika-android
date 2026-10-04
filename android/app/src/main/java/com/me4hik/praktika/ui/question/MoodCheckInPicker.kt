package com.me4hik.praktika.ui.question

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.mood.MoodCopy
import com.me4hik.praktika.data.mood.MoodCopyResolver
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.TextMuted
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextSecondary

@Composable
fun MoodCheckInPicker(
    wordingMode: QuestionWordingMode,
    selectedLevel: MoodLevel?,
    enabled: Boolean,
    onLevelSelected: (MoodLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val options = MoodCopyResolver.all(resources, wordingMode)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .testTag(PracticeTestTags.ANSWER_MOOD_PICKER),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { copy ->
            MoodOptionRow(
                copy = copy,
                selected = selectedLevel == copy.level,
                enabled = enabled,
                onClick = { onLevelSelected(copy.level) },
            )
        }
    }
}

@Composable
private fun MoodOptionRow(
    copy: MoodCopy,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val semanticsLabel = "${copy.title}. ${copy.explanation}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .semantics { contentDescription = semanticsLabel }
            .padding(vertical = 4.dp)
            .testTag(PracticeTestTags.moodOption(copy.level)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = AccentViolet,
                unselectedColor = TextMuted,
            ),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(moodLevelColor(copy.level)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = copy.emoji,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = copy.title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            Text(
                text = copy.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

fun moodLevelColor(level: MoodLevel): Color {
    return when (level) {
        MoodLevel.VERY_LOW -> Color(0xFFE57373)
        MoodLevel.LOW -> Color(0xFFFFB74D)
        MoodLevel.NEUTRAL -> Color(0xFFFFD54F)
        MoodLevel.GOOD -> Color(0xFF81C784)
        MoodLevel.GREAT -> Color(0xFF66BB6A)
    }
}
