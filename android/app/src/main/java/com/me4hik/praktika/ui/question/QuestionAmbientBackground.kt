// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - OnboardingScreen ambient background
package com.me4hik.praktika.ui.question

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle

@Composable
fun QuestionAmbientBackground(
    modifier: Modifier = Modifier,
) {
    PracticeBackground(
        modifier = modifier,
        style = PracticeBackgroundStyle.Question,
        heroGlowCenterFractionY = 0.54f,
    )
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
