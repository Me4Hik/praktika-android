package com.me4hik.praktika.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.data.preferences.AppLanguage
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeHeroAccent
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TitleSerifStyle

object LanguageTestTags {
    const val LANGUAGE_SCREEN = "language_screen"
    const val LANGUAGE_OPTION_RU = "language_option_ru"
    const val LANGUAGE_OPTION_UK = "language_option_uk"
    const val LANGUAGE_OPTION_EN = "language_option_en"
    const val LANGUAGE_OPTION_PL = "language_option_pl"
}

internal fun AppLanguage.optionLabelRes(): Int = when (this) {
    AppLanguage.RU -> R.string.language_option_ru
    AppLanguage.UK -> R.string.language_option_uk
    AppLanguage.EN -> R.string.language_option_en
    AppLanguage.PL -> R.string.language_option_pl
}

private fun AppLanguage.chooserTestTag(): String = when (this) {
    AppLanguage.RU -> LanguageTestTags.LANGUAGE_OPTION_RU
    AppLanguage.UK -> LanguageTestTags.LANGUAGE_OPTION_UK
    AppLanguage.EN -> LanguageTestTags.LANGUAGE_OPTION_EN
    AppLanguage.PL -> LanguageTestTags.LANGUAGE_OPTION_PL
}

@Composable
fun LanguageScreen(
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PracticeBackground(style = PracticeBackgroundStyle.Onboarding)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .testTag(LanguageTestTags.LANGUAGE_SCREEN),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            PracticeHeroAccent(widthFraction = 0.38f)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.language_screen_title),
                style = TitleSerifStyle,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            PracticeSurface {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppLanguage.DISPLAY_ORDER.forEach { language ->
                        LanguageOptionRow(
                            labelRes = language.optionLabelRes(),
                            testTag = language.chooserTestTag(),
                            onClick = { onLanguageSelected(language) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(
    labelRes: Int,
    testTag: String,
    onClick: () -> Unit,
) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.bodyLarge,
        color = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp)
            .testTag(testTag),
    )
}
