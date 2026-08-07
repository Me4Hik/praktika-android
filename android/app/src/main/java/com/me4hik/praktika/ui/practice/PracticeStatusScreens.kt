// 05.08.2026 Main Screen cursor by Me4Hik START - loading и fatal error экраны
package com.me4hik.praktika.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

@Composable
fun PracticeLoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(PracticeTestTags.LOADING)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun PracticeFatalErrorScreen(
    error: PracticeUiError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(PracticeTestTags.FATAL_ERROR)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (error) {
                PracticeUiError.LOAD_FAILED -> stringResource(R.string.practice_error_load_failed)
                PracticeUiError.START_FAILED -> stringResource(R.string.practice_error_start_failed)
                PracticeUiError.CORRUPTION -> stringResource(R.string.practice_error_corruption)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (error == PracticeUiError.LOAD_FAILED) {
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag(PracticeTestTags.RETRY_BUTTON),
            ) {
                Text(text = stringResource(R.string.practice_retry))
            }
        }
    }
}
// 05.08.2026 Main Screen cursor by Me4Hik END
