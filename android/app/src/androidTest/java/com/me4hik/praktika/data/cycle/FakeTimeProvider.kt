package com.me4hik.praktika.data.cycle

class FakeTimeProvider(
    private var epochMillis: Long,
    private var zoneId: String,
) : TimeProvider {
    override fun nowEpochMillis(): Long = epochMillis

    override fun currentZoneId(): String = zoneId

    fun setEpochMillis(value: Long) {
        epochMillis = value
    }

    fun advanceMillis(delta: Long) {
        epochMillis += delta
    }

    fun setZoneId(value: String) {
        zoneId = value
    }
}
