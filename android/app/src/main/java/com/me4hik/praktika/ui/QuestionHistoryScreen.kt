// 04.08.2026 Reminder App cursor by Me4Hik START - экран истории вопроса-заглушка
package com.me4hik.praktika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QuestionHistoryScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "История вопроса",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "[Заглушка] Какой настоящий я сейчас по цвету?",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Сохранённых ответов пока нет.",
            style = MaterialTheme.typography.bodyLarge
        )
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Назад")
        }
    }
}
// 04.08.2026 Reminder App cursor by Me4Hik END
