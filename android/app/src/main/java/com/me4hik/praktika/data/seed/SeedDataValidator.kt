// 04.08.2026 Seed Data cursor by Me4Hik START - валидатор начальных данных
package com.me4hik.praktika.data.seed

class SeedDataValidator {
    fun validate(seedData: SeedData) {
        if (seedData.seedVersion != EXPECTED_SEED_VERSION) {
            throw SeedValidationException(
                "seedVersion: expected $EXPECTED_SEED_VERSION, got ${seedData.seedVersion}",
            )
        }

        if (seedData.questions.size != EXPECTED_QUESTION_COUNT) {
            throw SeedValidationException(
                "questions: expected $EXPECTED_QUESTION_COUNT items, got ${seedData.questions.size}",
            )
        }

        if (seedData.defaultSlots.size != EXPECTED_SLOT_COUNT) {
            throw SeedValidationException(
                "defaultSlots: expected $EXPECTED_SLOT_COUNT items, got ${seedData.defaultSlots.size}",
            )
        }

        validateQuestions(seedData.questions)
        validateSlots(seedData.defaultSlots)
    }

    private fun validateQuestions(questions: List<SeedQuestion>) {
        val ids = mutableSetOf<Int>()
        val positions = mutableSetOf<Int>()
        val texts = mutableSetOf<String>()

        questions.forEach { question ->
            if (question.id !in EXPECTED_ID_RANGE) {
                throw SeedValidationException("questions[${question.id}].id: expected 1..21")
            }
            if (question.cyclePosition !in EXPECTED_ID_RANGE) {
                throw SeedValidationException(
                    "questions[${question.id}].cyclePosition: expected 1..21",
                )
            }
            if (question.id != question.cyclePosition) {
                throw SeedValidationException(
                    "questions[${question.id}]: id must equal cyclePosition",
                )
            }
            if (question.text.isEmpty()) {
                throw SeedValidationException("questions[${question.id}].text: must not be empty")
            }
            if (question.text != question.text.trim()) {
                throw SeedValidationException(
                    "questions[${question.id}].text: leading or trailing whitespace is not allowed",
                )
            }
            if (!question.isActive) {
                throw SeedValidationException("questions[${question.id}].isActive: expected true")
            }
            if (!ids.add(question.id)) {
                throw SeedValidationException("questions[${question.id}].id: duplicate id")
            }
            if (!positions.add(question.cyclePosition)) {
                throw SeedValidationException(
                    "questions[${question.id}].cyclePosition: duplicate cyclePosition",
                )
            }
            if (!texts.add(question.text)) {
                throw SeedValidationException("questions[${question.id}].text: duplicate text")
            }
        }

        EXPECTED_ID_RANGE.forEach { expectedId ->
            if (expectedId !in ids) {
                throw SeedValidationException("questions: missing id $expectedId")
            }
            if (expectedId !in positions) {
                throw SeedValidationException("questions: missing cyclePosition $expectedId")
            }
        }
    }

    private fun validateSlots(slots: List<SeedScheduleSlot>) {
        val indices = mutableSetOf<Int>()
        val minutes = mutableSetOf<Int>()
        val orderedMinutes = mutableListOf<Int>()

        slots.forEach { slot ->
            if (slot.slotIndex !in EXPECTED_SLOT_INDEX_RANGE) {
                throw SeedValidationException(
                    "defaultSlots[${slot.slotIndex}].slotIndex: expected 1..3",
                )
            }
            if (slot.timeOfDayMinutes !in MINUTES_RANGE) {
                throw SeedValidationException(
                    "defaultSlots[${slot.slotIndex}].timeOfDayMinutes: expected 0..1439",
                )
            }
            if (!indices.add(slot.slotIndex)) {
                throw SeedValidationException(
                    "defaultSlots[${slot.slotIndex}].slotIndex: duplicate slotIndex",
                )
            }
            if (!minutes.add(slot.timeOfDayMinutes)) {
                throw SeedValidationException(
                    "defaultSlots[${slot.slotIndex}].timeOfDayMinutes: duplicate time",
                )
            }
            orderedMinutes.add(slot.timeOfDayMinutes)
        }

        EXPECTED_SLOT_INDEX_RANGE.forEach { expectedIndex ->
            if (expectedIndex !in indices) {
                throw SeedValidationException("defaultSlots: missing slotIndex $expectedIndex")
            }
        }

        if (orderedMinutes.sorted() != orderedMinutes) {
            throw SeedValidationException("defaultSlots: times must be strictly ascending")
        }

        val expectedMinutes = listOf(660, 900, 1140)
        val actualByIndex = slots.sortedBy { it.slotIndex }.map { it.timeOfDayMinutes }
        if (actualByIndex != expectedMinutes) {
            throw SeedValidationException(
                "defaultSlots: expected minutes $expectedMinutes, got $actualByIndex",
            )
        }
    }

    companion object {
        const val EXPECTED_SEED_VERSION = 1
        const val EXPECTED_QUESTION_COUNT = 21
        const val EXPECTED_SLOT_COUNT = 3
        val EXPECTED_ID_RANGE = 1..21
        val EXPECTED_SLOT_INDEX_RANGE = 1..3
        val MINUTES_RANGE = 0..1439
    }
}
// 04.08.2026 Seed Data cursor by Me4Hik END
