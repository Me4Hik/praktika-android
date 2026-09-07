package com.me4hik.praktika.ui.tour

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.me4hik.praktika.data.preferences.praktikaPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

interface TesterToolsStore {
    val unlocked: Flow<Boolean>
    val lastTourResult: Flow<TourRunResult?>
    suspend fun setUnlocked(unlocked: Boolean)
    suspend fun saveTourResult(result: TourRunResult)
}

class DataStoreTesterToolsStore(
    context: Context,
) : TesterToolsStore {
    private val dataStore = context.applicationContext.praktikaPreferencesDataStore

    override val unlocked: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[UNLOCKED_KEY] == true }
        .catch { emit(false) }

    override val lastTourResult: Flow<TourRunResult?> = dataStore.data
        .map { prefs ->
            val json = prefs[RESULT_JSON_KEY] ?: return@map null
            parseResult(json)
        }
        .catch { emit(null) }

    override suspend fun setUnlocked(unlocked: Boolean) {
        dataStore.edit { prefs ->
            prefs[UNLOCKED_KEY] = unlocked
        }
    }

    override suspend fun saveTourResult(result: TourRunResult) {
        dataStore.edit { prefs ->
            prefs[RESULT_JSON_KEY] = serializeResult(result)
            prefs[RESULT_SAVED_AT_KEY] = result.endedAtEpochMs
        }
    }

    companion object {
        val UNLOCKED_KEY = booleanPreferencesKey("tester_tools_unlocked")
        val RESULT_JSON_KEY = stringPreferencesKey("tour_last_run_json")
        val RESULT_SAVED_AT_KEY = longPreferencesKey("tour_last_run_saved_at")

        fun serializeResult(result: TourRunResult): String {
            return JSONObject()
                .put("runId", result.runId)
                .put("startedAtEpochMs", result.startedAtEpochMs)
                .put("endedAtEpochMs", result.endedAtEpochMs)
                .put("completedStepIds", JSONArray(result.completedStepIds))
                .put("skippedStepIds", JSONArray(result.skippedStepIds))
                .put("exitStepId", result.exitStepId)
                .put("completedTour", result.completedTour)
                .toString()
        }

        fun parseResult(json: String): TourRunResult? {
            return runCatching {
                val obj = JSONObject(json)
                TourRunResult(
                    runId = obj.getString("runId"),
                    startedAtEpochMs = obj.getLong("startedAtEpochMs"),
                    endedAtEpochMs = obj.getLong("endedAtEpochMs"),
                    completedStepIds = obj.getJSONArray("completedStepIds").toStringList(),
                    skippedStepIds = obj.getJSONArray("skippedStepIds").toStringList(),
                    exitStepId = if (obj.isNull("exitStepId")) null else obj.getString("exitStepId"),
                    completedTour = obj.getBoolean("completedTour"),
                )
            }.getOrNull()
        }

        private fun JSONArray.toStringList(): List<String> {
            return buildList {
                for (i in 0 until length()) {
                    add(getString(i))
                }
            }
        }
    }
}
