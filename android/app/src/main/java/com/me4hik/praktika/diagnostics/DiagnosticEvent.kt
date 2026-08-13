// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import org.json.JSONObject

data class DiagnosticEvent(
    val seq: Long,
    val tsEpochMs: Long,
    val monoMs: Long,
    val category: DiagnosticCategory,
    val name: String,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun toJsonLine(): String {
        val metadataJson = JSONObject()
        metadata.forEach { (key, value) ->
            metadataJson.put(key, value)
        }
        return JSONObject().apply {
            put("seq", seq)
            put("tsEpochMs", tsEpochMs)
            put("monoMs", monoMs)
            put("category", category.name)
            put("name", name)
            put("metadata", metadataJson)
        }.toString()
    }

    companion object {
        fun fromJsonLine(line: String): DiagnosticEvent? {
            return try {
                val json = JSONObject(line)
                val metadataJson = json.optJSONObject("metadata") ?: JSONObject()
                val metadata = buildMap {
                    metadataJson.keys().forEach { key ->
                        put(key, metadataJson.optString(key))
                    }
                }
                DiagnosticEvent(
                    seq = json.getLong("seq"),
                    tsEpochMs = json.getLong("tsEpochMs"),
                    monoMs = json.getLong("monoMs"),
                    category = DiagnosticCategory.valueOf(json.getString("category")),
                    name = json.getString("name"),
                    metadata = metadata,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
