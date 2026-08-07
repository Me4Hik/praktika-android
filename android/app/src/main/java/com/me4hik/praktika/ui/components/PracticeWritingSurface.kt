// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - Answer glass writing surface
package com.me4hik.praktika.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.TextMuted
import com.me4hik.praktika.ui.theme.TextPrimary

@Composable
fun PracticeWritingSurface(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    minLines: Int = 8,
    testTag: String? = null,
) {
    PracticeSurface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 200.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (testTag != null) {
                        Modifier.testTag(testTag)
                    } else {
                        Modifier
                    },
                ),
            enabled = enabled,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted,
                )
            },
            singleLine = false,
            minLines = minLines,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                cursorColor = AccentViolet,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = TextPrimary.copy(alpha = 0.55f),
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
        )
    }
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
