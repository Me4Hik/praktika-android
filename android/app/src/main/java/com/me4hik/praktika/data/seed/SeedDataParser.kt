// 04.08.2026 Seed Data cursor by Me4Hik START - парсер questions.json через org.json
package com.me4hik.praktika.data.seed

import org.json.JSONArray
import org.json.JSONObject

class SeedDataParser {
    fun parse(json: String): SeedData {
        val root = try {
            JSONObject(json)
        } catch (exception: Exception) {
            throw SeedParseException("root: invalid JSON document", exception)
        }

        assertNoUnknownFields(root, ROOT_FIELDS, "root")

        val seedVersion = requireInt(root, "seedVersion", "root.seedVersion")
        val questionsArray = requireArray(root, "questions", "root.questions")
        val slotsArray = requireArray(root, "defaultSlots", "root.defaultSlots")

        val questions = parseQuestions(questionsArray)
        val defaultSlots = parseSlots(slotsArray)

        return SeedData(
            seedVersion = seedVersion,
            questions = questions,
            defaultSlots = defaultSlots,
        )
    }

    private fun parseQuestions(array: JSONArray): List<SeedQuestion> {
        return buildList {
            for (index in 0 until array.length()) {
                val path = "questions[$index]"
                val item = array.optJSONObject(index)
                    ?: throw SeedParseException("$path: expected JSONObject")
                assertNoUnknownFields(item, QUESTION_FIELDS, path)
                add(
                    SeedQuestion(
                        id = requireInt(item, "id", "$path.id"),
                        cyclePosition = requireInt(item, "cyclePosition", "$path.cyclePosition"),
                        text = requireString(item, "text", "$path.text"),
                        isActive = requireBoolean(item, "isActive", "$path.isActive"),
                    ),
                )
            }
        }
    }

    private fun parseSlots(array: JSONArray): List<SeedScheduleSlot> {
        return buildList {
            for (index in 0 until array.length()) {
                val path = "defaultSlots[$index]"
                val item = array.optJSONObject(index)
                    ?: throw SeedParseException("$path: expected JSONObject")
                assertNoUnknownFields(item, SLOT_FIELDS, path)
                add(
                    SeedScheduleSlot(
                        slotIndex = requireInt(item, "slotIndex", "$path.slotIndex"),
                        timeOfDayMinutes = requireInt(item, "timeOfDayMinutes", "$path.timeOfDayMinutes"),
                    ),
                )
            }
        }
    }

    private fun assertNoUnknownFields(
        jsonObject: JSONObject,
        allowedFields: Set<String>,
        path: String,
    ) {
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in allowedFields) {
                throw SeedParseException("$path.$key: unknown field")
            }
        }
    }

    private fun requireInt(jsonObject: JSONObject, field: String, path: String): Int {
        if (!jsonObject.has(field) || jsonObject.isNull(field)) {
            throw SeedParseException("$path: required Int field is missing or null")
        }
        return try {
            jsonObject.getInt(field)
        } catch (exception: Exception) {
            throw SeedParseException("$path: expected Int", exception)
        }
    }

    private fun requireBoolean(jsonObject: JSONObject, field: String, path: String): Boolean {
        if (!jsonObject.has(field) || jsonObject.isNull(field)) {
            throw SeedParseException("$path: required Boolean field is missing or null")
        }
        return try {
            jsonObject.getBoolean(field)
        } catch (exception: Exception) {
            throw SeedParseException("$path: expected Boolean", exception)
        }
    }

    private fun requireString(jsonObject: JSONObject, field: String, path: String): String {
        if (!jsonObject.has(field) || jsonObject.isNull(field)) {
            throw SeedParseException("$path: required String field is missing or null")
        }
        return try {
            jsonObject.getString(field)
        } catch (exception: Exception) {
            throw SeedParseException("$path: expected String", exception)
        }
    }

    private fun requireArray(jsonObject: JSONObject, field: String, path: String): JSONArray {
        if (!jsonObject.has(field) || jsonObject.isNull(field)) {
            throw SeedParseException("$path: required JSONArray field is missing or null")
        }
        return try {
            jsonObject.getJSONArray(field)
        } catch (exception: Exception) {
            throw SeedParseException("$path: expected JSONArray", exception)
        }
    }

    private companion object {
        val ROOT_FIELDS = setOf("seedVersion", "questions", "defaultSlots")
        val QUESTION_FIELDS = setOf("id", "cyclePosition", "text", "isActive")
        val SLOT_FIELDS = setOf("slotIndex", "timeOfDayMinutes")
    }
}
// 04.08.2026 Seed Data cursor by Me4Hik END
