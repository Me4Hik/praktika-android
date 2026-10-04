package com.me4hik.praktika.ui.measurement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.me4hik.praktika.R
import com.me4hik.praktika.measurement.DebugAnalyticsEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AnalyticsDiagnosticsTestTags {
    const val SCREEN = "analytics_diagnostics_screen"
    const val SEND_TEST = "analytics_diagnostics_send_test"
    const val CLEAR = "analytics_diagnostics_clear"
    const val EVENT_LIST = "analytics_diagnostics_event_list"
    const val PROVIDER_STATUS = "analytics_diagnostics_provider_status"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDiagnosticsScreen(
    viewModel: AnalyticsDiagnosticsViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AnalyticsDiagnosticsTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.analytics_diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.practice_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.analytics_diagnostics_providers),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(AnalyticsDiagnosticsTestTags.PROVIDER_STATUS),
            )
            Text(
                text = stringResource(R.string.analytics_diagnostics_provider_debug_active),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.analytics_diagnostics_provider_firebase_not_configured),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.analytics_diagnostics_provider_meta_not_configured),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = viewModel::sendTestEvent,
                modifier = Modifier.testTag(AnalyticsDiagnosticsTestTags.SEND_TEST),
            ) {
                Text(text = stringResource(R.string.analytics_diagnostics_send_test))
            }
            TextButton(
                onClick = viewModel::clear,
                modifier = Modifier.testTag(AnalyticsDiagnosticsTestTags.CLEAR),
            ) {
                Text(text = stringResource(R.string.analytics_diagnostics_clear))
            }
            Text(
                text = stringResource(R.string.analytics_diagnostics_recent_events),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(AnalyticsDiagnosticsTestTags.EVENT_LIST),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries.asReversed(), key = { it.sequence }) { entry ->
                    AnalyticsDiagnosticsEventRow(entry)
                }
            }
        }
    }
}

@Composable
private fun AnalyticsDiagnosticsEventRow(entry: DebugAnalyticsEntry) {
    val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    val time = formatter.format(Date(entry.timestampEpochMillis))
    val params = if (entry.params.isEmpty()) {
        "—"
    } else {
        entry.params.entries.joinToString { "${it.key}=${it.value}" }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$time · ${entry.eventName}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(
                R.string.analytics_diagnostics_event_meta,
                params,
                entry.routedProviders.joinToString(","),
                entry.deliveryStatus,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
