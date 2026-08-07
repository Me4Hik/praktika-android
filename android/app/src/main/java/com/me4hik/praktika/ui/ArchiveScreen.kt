// 04.08.2026 Reminder App cursor by Me4Hik START - экран архива-заглушка
package com.me4hik.praktika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ArchiveScreen(
    onOpenQuestionHistory: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Архив ответов",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Режимы просмотра (заглушка). Реальные данные появятся на следующих этапах.",
            style = MaterialTheme.typography.bodyLarge
        )
        OutlinedButton(
            onClick = { },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "По датам")
        }
        OutlinedButton(
            onClick = { },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "По вопросам")
        }
        Button(
            onClick = onOpenQuestionHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "История тестового вопроса")
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Назад")
        }
    }
}
// 04.08.2026 Reminder App cursor by Me4Hik END
