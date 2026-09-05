// 05.09.2026 Question Screen Adaptivity cursor by Me4Hik START - interactive preview matrix
package com.me4hik.praktika.ui.question

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.ui.QuestionScreen
import com.me4hik.praktika.ui.practice.QuestionCommandState
import com.me4hik.praktika.ui.practice.QuestionUiModel
import com.me4hik.praktika.ui.practice.QuestionUiState
import com.me4hik.praktika.ui.theme.PraktikaTheme

private const val SHORT_QUESTION = "Что сейчас живого?"

private const val MEDIUM_QUESTION =
    "Какое ощущение в теле просит внимания прямо сейчас, без оценки и без спешки?"

private const val LONG_QUESTION =
    "Если отложить привычные объяснения и просто заметить, что происходит внутри " +
        "в этот момент — какое качество присутствует сильнее всего, и где в теле оно " +
        "ощущается яснее всего, даже если слова даются с трудом и мысль ускользает?"

private fun previewQuestion(text: String): QuestionUiModel {
    return QuestionUiModel(
        occurrenceId = 1L,
        questionId = 1,
        questionTextSnapshot = text,
        cyclePosition = 3,
        status = QuestionOccurrenceStatus.AVAILABLE,
    )
}

@Composable
private fun QuestionInteractivePreview(
    questionText: String,
) {
    PraktikaTheme {
        QuestionScreen(
            uiState = QuestionUiState.Interactive(previewQuestion(questionText)),
            commandState = QuestionCommandState(),
            onAnswer = {},
            onDefer = {},
            onSkip = {},
            onBackHome = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Phone 360x640 short", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun PreviewQuestion360x640Short() {
    QuestionInteractivePreview(SHORT_QUESTION)
}

@Preview(name = "Phone 360x800 medium", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun PreviewQuestion360x800Medium() {
    QuestionInteractivePreview(MEDIUM_QUESTION)
}

@Preview(name = "Phone 360x915 tall compact", widthDp = 360, heightDp = 915, showBackground = true)
@Composable
private fun PreviewQuestion360x915TallCompact() {
    QuestionInteractivePreview(MEDIUM_QUESTION)
}

@Preview(name = "Phone 411x891 short", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun PreviewQuestion411x891Short() {
    QuestionInteractivePreview(SHORT_QUESTION)
}

@Preview(name = "Phone 411x891 long multiline", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun PreviewQuestion411x891Long() {
    QuestionInteractivePreview(LONG_QUESTION)
}

@Preview(
    name = "Phone 411x891 fontScale 1.3",
    widthDp = 411,
    heightDp = 891,
    fontScale = 1.3f,
    showBackground = true,
)
@Composable
private fun PreviewQuestion411x891FontScale() {
    QuestionInteractivePreview(MEDIUM_QUESTION)
}

@Preview(name = "Phone 412x915 tall compact", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PreviewQuestion412x915TallCompact() {
    QuestionInteractivePreview(MEDIUM_QUESTION)
}

@Preview(name = "Phone 430x932 tall compact", widthDp = 430, heightDp = 932, showBackground = true)
@Composable
private fun PreviewQuestion430x932TallCompact() {
    QuestionInteractivePreview(MEDIUM_QUESTION)
}

@Preview(name = "Tablet 600x960 medium", widthDp = 600, heightDp = 960, showBackground = true)
@Composable
private fun PreviewQuestion600x960Medium() {
    QuestionInteractivePreview(MEDIUM_QUESTION)
}

@Preview(name = "Tablet 800x1280 long", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun PreviewQuestion800x1280Long() {
    QuestionInteractivePreview(LONG_QUESTION)
}

@Preview(name = "Tablet 840x1344 short", widthDp = 840, heightDp = 1344, showBackground = true)
@Composable
private fun PreviewQuestion840x1344Short() {
    QuestionInteractivePreview(SHORT_QUESTION)
}

@Preview(
    name = "Landscape 1280x800 medium",
    widthDp = 1280,
    heightDp = 800,
    showBackground = true,
)
@Composable
private fun PreviewQuestion1280x800Landscape() {
    QuestionInteractivePreview(MEDIUM_QUESTION)
}
// 05.09.2026 Question Screen Adaptivity cursor by Me4Hik END
