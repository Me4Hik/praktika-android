// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - quiet archive top bar
package com.me4hik.praktika.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.theme.TextSecondary

@Composable
fun PracticeTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backTestTag: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (backTestTag != null) {
                        Modifier.testTag(backTestTag)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.archive_back),
                tint = TextSecondary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
