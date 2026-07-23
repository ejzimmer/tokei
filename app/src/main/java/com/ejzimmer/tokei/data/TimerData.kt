package com.ejzimmer.tokei.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TimerStatus { IDLE, RUNNING, PAUSED, RINGING }

/**
 * One timer's full state. hours/minutes/seconds are the *configured* duration
 * and never change while running/paused/ringing -- endAtEpochMs and
 * pausedRemainingMs track the live countdown separately, mirroring the
 * original web version's model.
 */
data class TimerData(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var hours: Int = 0,
    var minutes: Int = 5,
    var seconds: Int = 0,
    var soundId: String,
    var status: TimerStatus = TimerStatus.IDLE,
    var endAtEpochMs: Long? = null,
    var pausedRemainingMs: Long? = null,
    var finishedAtEpochMs: Long? = null,
    var lastFinishedAtEpochMs: Long? = null,
) {
    fun durationMs(): Long = ((hours * 60L + minutes) * 60L + seconds) * 1000L

    /** Carries overflowing seconds into minutes, and overflowing minutes into hours. */
    fun normalize() {
        if (seconds >= 60) {
            minutes += seconds / 60
            seconds %= 60
        }
        if (minutes >= 60) {
            hours += minutes / 60
            minutes %= 60
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("hours", hours)
        put("minutes", minutes)
        put("seconds", seconds)
        put("soundId", soundId)
        put("status", status.name)
        put("endAtEpochMs", endAtEpochMs ?: JSONObject.NULL)
        put("pausedRemainingMs", pausedRemainingMs ?: JSONObject.NULL)
        put("finishedAtEpochMs", finishedAtEpochMs ?: JSONObject.NULL)
        put("lastFinishedAtEpochMs", lastFinishedAtEpochMs ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): TimerData = TimerData(
            id = json.getString("id"),
            name = json.getString("name"),
            hours = json.optInt("hours", 0),
            minutes = json.optInt("minutes", 5),
            seconds = json.optInt("seconds", 0),
            soundId = json.getString("soundId"),
            status = runCatching { TimerStatus.valueOf(json.getString("status")) }
                .getOrDefault(TimerStatus.IDLE),
            endAtEpochMs = json.optLongOrNull("endAtEpochMs"),
            pausedRemainingMs = json.optLongOrNull("pausedRemainingMs"),
            finishedAtEpochMs = json.optLongOrNull("finishedAtEpochMs"),
            lastFinishedAtEpochMs = json.optLongOrNull("lastFinishedAtEpochMs"),
        )
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)

fun List<TimerData>.toJsonString(): String {
    val array = JSONArray()
    forEach { array.put(it.toJson()) }
    return array.toString()
}

fun parseTimerList(json: String): List<TimerData> {
    val array = JSONArray(json)
    return (0 until array.length()).map { i -> TimerData.fromJson(array.getJSONObject(i)) }
}
