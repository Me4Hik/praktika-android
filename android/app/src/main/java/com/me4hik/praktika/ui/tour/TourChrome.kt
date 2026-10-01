package com.me4hik.praktika.ui.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextSecondary

@Composable
fun TourChrome(
    tip: String,
    stepOrdinal: Int,
    stepCount: Int,
    showNext: Boolean,
    placeBelow: Boolean,
    onSkip: () -> Unit,
    onExit: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(
                top = if (placeBelow) 0.dp else 48.dp,
                bottom = if (placeBelow) 48.dp else 0.dp,
            )
            .then(
                if (placeBelow) {
                    Modifier.padding(top = 12.dp)
                } else {
                    Modifier
                },
            )
            .testTag(TourOverlayTestTags.CHROME),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xF0121218), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.tour_progress, stepOrdinal, stepCount),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.testTag(TourOverlayTestTags.PROGRESS),
            )
            Text(
                text = tip,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier.testTag(TourOverlayTestTags.TIP),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onExit,
                    modifier = Modifier.testTag(TourOverlayTestTags.EXIT),
                ) {
                    Text(text = stringResource(R.string.tour_exit))
                }
                Row {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.testTag(TourOverlayTestTags.SKIP),
                    ) {
                        Text(text = stringResource(R.string.tour_skip))
                    }
                    if (showNext) {
                        TextButton(
                            onClick = onNext,
                            modifier = Modifier.testTag(TourOverlayTestTags.NEXT),
                        ) {
                            Text(text = stringResource(R.string.tour_next))
                        }
                    }
                }
            }
        }
    }
}
