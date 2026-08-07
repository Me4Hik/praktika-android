// 04.08.2026 Cycle Engine cursor by Me4Hik START - чистый расчёт календарных слотов
package com.me4hik.praktika.data.cycle

import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class ScheduleCalculator {
    fun findStartSlot(
        nowEpochMillis: Long,
        zoneId: String,
        slots: List<ScheduleSlotEntity>,
    ): SlotMoment {
        val sortedSlots = validateAndSort(slots)
        val zone = parseZone(zoneId)
        val nowZoned = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
        val currentMinute = nowZoned.truncatedTo(ChronoUnit.MINUTES)
        val minutesOfDay = currentMinute.hour * MINUTES_PER_HOUR + currentMinute.minute
        val localDate = currentMinute.toLocalDate()

        sortedSlots.firstOrNull { it.timeOfDayMinutes == minutesOfDay }?.let { slot ->
            return buildMoment(localDate, slot, zoneId, zone)
        }

        sortedSlots.firstOrNull { it.timeOfDayMinutes > minutesOfDay }?.let { slot ->
            return buildMoment(localDate, slot, zoneId, zone)
        }

        return buildMoment(localDate.plusDays(1), sortedSlots.first(), zoneId, zone)
    }

    fun findStrictlyNextSlot(
        afterEpochMillis: Long,
        zoneId: String,
        slots: List<ScheduleSlotEntity>,
    ): SlotMoment {
        val sortedSlots = validateAndSort(slots)
        val zone = parseZone(zoneId)
        var date = Instant.ofEpochMilli(afterEpochMillis).atZone(zone).toLocalDate()

        repeat(MAX_DAY_SEARCH) {
            for (slot in sortedSlots) {
                val moment = buildMoment(date, slot, zoneId, zone)
                if (moment.plannedAtEpochMillis > afterEpochMillis) {
                    return moment
                }
            }
            date = date.plusDays(1)
        }

        throw CycleInvalidScheduleException(
            "Unable to find strictly next slot after $afterEpochMillis in zone $zoneId",
        )
    }

    fun findNextSlotAfterMoment(
        current: SlotMoment,
        slots: List<ScheduleSlotEntity>,
    ): SlotMoment {
        val sortedSlots = validateAndSort(slots)
        val zone = parseZone(current.zoneId)
        val currentZoned = Instant.ofEpochMilli(current.plannedAtEpochMillis).atZone(zone)
        val currentDate = currentZoned.toLocalDate()
        val currentIndex = sortedSlots.indexOfFirst { it.slotIndex == current.slotIndex }
        if (currentIndex < 0) {
            throw CycleInvalidScheduleException("Unknown slot index ${current.slotIndex}")
        }

        if (currentIndex < sortedSlots.lastIndex) {
            return buildMoment(currentDate, sortedSlots[currentIndex + 1], current.zoneId, zone)
        }

        return buildMoment(currentDate.plusDays(1), sortedSlots.first(), current.zoneId, zone)
    }

    fun calculateAvailableUntil(
        plannedMoment: SlotMoment,
        slots: List<ScheduleSlotEntity>,
    ): Long = findNextSlotAfterMoment(plannedMoment, slots).plannedAtEpochMillis

    fun resolveSlotMoment(
        plannedAtEpochMillis: Long,
        zoneId: String,
        slots: List<ScheduleSlotEntity>,
    ): SlotMoment {
        val sortedSlots = validateAndSort(slots)
        val zone = parseZone(zoneId)
        val zoned = Instant.ofEpochMilli(plannedAtEpochMillis).atZone(zone)
        val minutesOfDay = zoned.hour * MINUTES_PER_HOUR + zoned.minute
        val slot = sortedSlots.firstOrNull { it.timeOfDayMinutes == minutesOfDay }
            ?: throw CycleInvalidScheduleException(
                "No schedule slot matches plannedAt=$plannedAtEpochMillis in zone $zoneId",
            )
        return SlotMoment(
            slotIndex = slot.slotIndex,
            plannedAtEpochMillis = zoned.truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli(),
            zoneId = zoneId,
        )
    }

    fun isWithinSlotMinute(
        nowEpochMillis: Long,
        slotPlannedAtEpochMillis: Long,
        zoneId: String,
    ): Boolean {
        val zone = parseZone(zoneId)
        val nowMinute = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).truncatedTo(ChronoUnit.MINUTES)
        val slotMinute = Instant.ofEpochMilli(slotPlannedAtEpochMillis).atZone(zone).truncatedTo(ChronoUnit.MINUTES)
        return nowMinute == slotMinute
    }

    fun validateAndSort(slots: List<ScheduleSlotEntity>): List<ScheduleSlotEntity> {
        if (slots.isEmpty()) {
            throw CycleInvalidScheduleException("Schedule slots must not be empty")
        }

        val sorted = slots.sortedBy { it.timeOfDayMinutes }
        val slotIndexes = sorted.map { it.slotIndex }
        val minutes = sorted.map { it.timeOfDayMinutes }

        if (slotIndexes.toSet().size != slotIndexes.size) {
            throw CycleInvalidScheduleException("slotIndex values must be unique")
        }
        if (minutes.toSet().size != minutes.size) {
            throw CycleInvalidScheduleException("timeOfDayMinutes values must be unique")
        }
        minutes.forEach { minute ->
            if (minute !in 0 until MINUTES_PER_DAY) {
                throw CycleInvalidScheduleException("timeOfDayMinutes must be in 0..1439, was $minute")
            }
        }

        return sorted
    }

    fun parseZone(zoneId: String): ZoneId {
        return try {
            ZoneId.of(zoneId)
        } catch (exception: Exception) {
            throw CycleInvalidScheduleException("Unknown zoneId: $zoneId", exception)
        }
    }

    private fun buildMoment(
        date: LocalDate,
        slot: ScheduleSlotEntity,
        zoneId: String,
        zone: ZoneId,
    ): SlotMoment {
        val localTime = LocalTime.of(
            slot.timeOfDayMinutes / MINUTES_PER_HOUR,
            slot.timeOfDayMinutes % MINUTES_PER_HOUR,
        )
        val zonedDateTime = ZonedDateTime.of(date, localTime, zone)
        return SlotMoment(
            slotIndex = slot.slotIndex,
            plannedAtEpochMillis = zonedDateTime.truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli(),
            zoneId = zoneId,
        )
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 1440
        private const val MAX_DAY_SEARCH = 366
    }
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END
