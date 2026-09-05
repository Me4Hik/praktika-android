// 05.09.2026 Question Screen Adaptivity cursor by Me4Hik START - local layout tokens
package com.me4hik.praktika.ui.question

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.me4hik.praktika.ui.theme.QuestionSerifStyle

enum class QuestionWidthBucket {
    Compact,
    Medium,
    Expanded,
}

@Immutable
data class QuestionLayoutTokens(
    val widthBucket: QuestionWidthBucket,
    val horizontalPadding: Dp,
    val contentMaxWidth: Dp,
    val clusterTopPadding: Dp,
    val metaToQuestionGap: Dp,
    val questionToDropGap: Dp,
    val questionTextStyle: TextStyle,
    val orbMinHeight: Dp,
    val orbMaxHeight: Dp,
    val orbWidthFraction: Float,
    val orbMaxWidth: Dp,
    val useCenteredCluster: Boolean,
)

@Composable
fun rememberQuestionLayoutTokens(): QuestionLayoutTokens {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    return remember(widthDp) {
        questionLayoutTokensFor(widthDp = widthDp)
    }
}

internal fun questionLayoutTokensFor(
    widthDp: Int,
): QuestionLayoutTokens {
    val widthBucket = when {
        widthDp >= 840 -> QuestionWidthBucket.Expanded
        widthDp >= 600 -> QuestionWidthBucket.Medium
        else -> QuestionWidthBucket.Compact
    }
    val useCenteredCluster = widthBucket != QuestionWidthBucket.Compact

    return when (widthBucket) {
        QuestionWidthBucket.Compact -> QuestionLayoutTokens(
            widthBucket = widthBucket,
            horizontalPadding = 24.dp,
            contentMaxWidth = Dp.Infinity,
            clusterTopPadding = 28.dp,
            metaToQuestionGap = 18.dp,
            questionToDropGap = 6.dp,
            questionTextStyle = QuestionSerifStyle,
            orbMinHeight = 200.dp,
            orbMaxHeight = 260.dp,
            orbWidthFraction = 0.62f,
            orbMaxWidth = Dp.Infinity,
            useCenteredCluster = useCenteredCluster,
        )
        QuestionWidthBucket.Medium -> QuestionLayoutTokens(
            widthBucket = widthBucket,
            horizontalPadding = 40.dp,
            contentMaxWidth = 520.dp,
            clusterTopPadding = 24.dp,
            metaToQuestionGap = 16.dp,
            questionToDropGap = 12.dp,
            questionTextStyle = QuestionSerifStyle.copy(
                fontSize = 32.sp,
                lineHeight = 42.sp,
            ),
            orbMinHeight = 200.dp,
            orbMaxHeight = 260.dp,
            orbWidthFraction = 0.58f,
            orbMaxWidth = 300.dp,
            useCenteredCluster = useCenteredCluster,
        )
        QuestionWidthBucket.Expanded -> QuestionLayoutTokens(
            widthBucket = widthBucket,
            horizontalPadding = 56.dp,
            contentMaxWidth = 560.dp,
            clusterTopPadding = 24.dp,
            metaToQuestionGap = 16.dp,
            questionToDropGap = 14.dp,
            questionTextStyle = QuestionSerifStyle.copy(
                fontSize = 34.sp,
                lineHeight = 46.sp,
            ),
            orbMinHeight = 200.dp,
            orbMaxHeight = 280.dp,
            orbWidthFraction = 0.55f,
            orbMaxWidth = 320.dp,
            useCenteredCluster = useCenteredCluster,
        )
    }
}
// 05.09.2026 Question Screen Adaptivity cursor by Me4Hik END
