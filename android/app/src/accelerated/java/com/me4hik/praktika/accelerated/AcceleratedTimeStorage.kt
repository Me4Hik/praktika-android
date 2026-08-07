// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - AtomicFile storage
package com.me4hik.praktika.accelerated

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AcceleratedTimeStorage(
    context: Context,
) : AcceleratedClockPersister {
    private val appContext = context.applicationContext
    private val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
    private val ioLock = Any()

    suspend fun loadOrCreateInitial(monotonic: MonotonicTimeSource): AcceleratedClockState =
        withContext(Dispatchers.IO) {
            synchronized(ioLock) {
                if (!stateFile.exists()) {
                    val initial = createInitialState(monotonic)
                    writeState(initial)
                    initial
                } else {
                    readState()
                }
            }
        }

    override suspend fun save(state: AcceleratedClockState) = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            state.validate()
            writeState(state)
        }
    }

    suspend fun readExisting(): AcceleratedClockState? = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            if (!stateFile.exists()) {
                null
            } else {
                readState()
            }
        }
    }

    private fun createInitialState(monotonic: MonotonicTimeSource): AcceleratedClockState {
        val zoneId = monotonic.currentZoneId()
        val zone = ZoneId.of(zoneId)
        val today = LocalDate.now(zone)
        val startOfDay = ZonedDateTime.of(today.year, today.monthValue, today.dayOfMonth, 8, 0, 0, 0, zone)
        val virtualStart = startOfDay.toInstant().toEpochMilli()
        return AcceleratedClockState.createInitial(
            zoneId = zoneId,
            virtualStartEpochMillis = virtualStart,
            bootCount = monotonic.bootCount(),
            realAnchorElapsedRealtimeMillis = monotonic.elapsedRealtimeMillis(),
        )
    }

    private fun readState(): AcceleratedClockState {
        val atomicFile = AtomicFile(stateFile)
        val bytes = atomicFile.readFully()
        val jsonText = bytes.toString(Charsets.UTF_8)
        return parseJson(jsonText)
    }

    private fun writeState(state: AcceleratedClockState) {
        val atomicFile = AtomicFile(stateFile)
        val json = serializeJson(state)
        val bytes = json.toByteArray(Charsets.UTF_8)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            atomicFile.finishWrite(stream)
            stream = null
        } catch (exception: Exception) {
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
            throw exception
        }
    }

    internal fun parseJson(jsonText: String): AcceleratedClockState {
        return try {
            val json = JSONObject(jsonText)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in KNOWN_FIELDS) {
                    throw AcceleratedClockParseException("Unknown field: $key")
                }
            }
            AcceleratedClockState(
                schemaVersion = json.getInt("schemaVersion"),
                bootCount = json.getInt("bootCount"),
                realAnchorElapsedRealtimeMillis = json.getLong("realAnchorElapsedRealtimeMillis"),
                virtualAnchorEpochMillis = json.getLong("virtualAnchorEpochMillis"),
                speedMultiplier = json.getInt("speedMultiplier"),
                isVirtualClockPaused = json.getBoolean("isVirtualClockPaused"),
                pausedVirtualEpochMillis = if (json.has("pausedVirtualEpochMillis") && !json.isNull("pausedVirtualEpochMillis")) {
                    json.getLong("pausedVirtualEpochMillis")
                } else {
                    null
                },
                zoneId = json.getString("zoneId"),
                lastCheckpointVirtualEpochMillis = json.getLong("lastCheckpointVirtualEpochMillis"),
            )
        } catch (exception: AcceleratedClockCorruptionException) {
            throw exception
        } catch (exception: AcceleratedClockParseException) {
            throw exception
        } catch (exception: Exception) {
            throw AcceleratedClockParseException("Failed to parse accelerated clock state", exception)
        }
    }

    internal fun serializeJson(state: AcceleratedClockState): String {
        val json = JSONObject()
        json.put("schemaVersion", state.schemaVersion)
        json.put("bootCount", state.bootCount)
        json.put("realAnchorElapsedRealtimeMillis", state.realAnchorElapsedRealtimeMillis)
        json.put("virtualAnchorEpochMillis", state.virtualAnchorEpochMillis)
        json.put("speedMultiplier", state.speedMultiplier)
        json.put("isVirtualClockPaused", state.isVirtualClockPaused)
        if (state.pausedVirtualEpochMillis != null) {
            json.put("pausedVirtualEpochMillis", state.pausedVirtualEpochMillis)
        } else {
            json.put("pausedVirtualEpochMillis", JSONObject.NULL)
        }
        json.put("zoneId", state.zoneId)
        json.put("lastCheckpointVirtualEpochMillis", state.lastCheckpointVirtualEpochMillis)
        return json.toString()
    }

    companion object {
        const val STATE_FILE_NAME = "accelerated_time_state.json"

        private val KNOWN_FIELDS = setOf(
            "schemaVersion",
            "bootCount",
            "realAnchorElapsedRealtimeMillis",
            "virtualAnchorEpochMillis",
            "speedMultiplier",
            "isVirtualClockPaused",
            "pausedVirtualEpochMillis",
            "zoneId",
            "lastCheckpointVirtualEpochMillis",
        )
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
