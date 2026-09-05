// PROMPT 129 — analytics read repository (terminals + defer_events, stored zoneId)
package com.me4hik.praktika.data.read

import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.model.AnalyticsDeferEventRow
import com.me4hik.praktika.data.local.model.AnalyticsTerminalOccurrenceRow

interface AnalyticsReadRepository {
    suspend fun getTerminalRows(): List<AnalyticsTerminalOccurrenceRow>

    suspend fun getDeferRows(): List<AnalyticsDeferEventRow>
}

class RoomAnalyticsReadRepository(
    private val database: PraktikaDatabase,
) : AnalyticsReadRepository {
    override suspend fun getTerminalRows(): List<AnalyticsTerminalOccurrenceRow> {
        return database.questionOccurrenceDao().getAnalyticsTerminalRows()
    }

    override suspend fun getDeferRows(): List<AnalyticsDeferEventRow> {
        return database.deferEventDao().getAnalyticsDeferRows()
    }
}
