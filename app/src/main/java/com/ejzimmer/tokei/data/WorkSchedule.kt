package com.ejzimmer.tokei.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * The fixed rules behind the Work timer: which days owe hours, how long a
 * day's cycle is, and when the two nudge notifications fire. Deliberately
 * constants rather than a settings screen -- there's exactly one person
 * using this and exactly one schedule.
 */
object WorkSchedule {
    val WORK_DAYS = setOf(
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    /** 7.5 hours -- one work day's worth of countdown. */
    const val CYCLE_MS = 7L * 60L * 60L * 1000L + 30L * 60L * 1000L

    /** Not started by this time on a work day -> "did you forget to start?" */
    val MORNING_REMINDER: LocalTime = LocalTime.of(8, 30)

    /** Still running at this time -> "did you forget to stop?" */
    val EVENING_REMINDER: LocalTime = LocalTime.of(18, 30)

    fun isWorkDay(date: LocalDate): Boolean = date.dayOfWeek in WORK_DAYS

    /** [date] itself when it's a work day, otherwise the next one after it. */
    fun firstWorkDayOnOrAfter(date: LocalDate): LocalDate {
        var day = date
        // At most 7 steps -- WORK_DAYS is never empty.
        while (!isWorkDay(day)) day = day.plusDays(1)
        return day
    }

    fun epochMsOf(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * The next Tuesday-to-Friday 8:30 strictly after [from]. The weekend and
     * Monday are skipped outright, so the "did you forget to start?" nudge
     * can never land on a day that isn't normally worked.
     */
    fun nextMorningReminderAfter(from: LocalDateTime): Long {
        var day = firstWorkDayOnOrAfter(from.toLocalDate())
        if (day == from.toLocalDate() && !from.toLocalTime().isBefore(MORNING_REMINDER)) {
            day = firstWorkDayOnOrAfter(day.plusDays(1))
        }
        return epochMsOf(day, MORNING_REMINDER)
    }

    /**
     * The next 18:30, any day. This one needs no day-of-week filter: it only
     * ever speaks up when the timer is genuinely still running, so a day off
     * stays quiet on its own.
     */
    fun nextEveningReminderAfter(from: LocalDateTime): Long {
        val day = if (from.toLocalTime().isBefore(EVENING_REMINDER)) {
            from.toLocalDate()
        } else {
            from.toLocalDate().plusDays(1)
        }
        return epochMsOf(day, EVENING_REMINDER)
    }
}

fun localDateOf(epochMs: Long): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

private val weekdayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE")
private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/** "today" / "tomorrow" / "Thursday" / "Thu 18 Sep", whichever reads best. */
fun workDayLabel(day: LocalDate, today: LocalDate): String = when {
    day == today -> "today"
    day == today.plusDays(1) -> "tomorrow"
    day == today.minusDays(1) -> "yesterday"
    abs(day.toEpochDay() - today.toEpochDay()) < 7 -> day.format(weekdayFormat)
    else -> day.format(dateFormat)
}

/** Hours and minutes only -- seconds are noise at this scale. */
fun formatWorkDuration(ms: Long): String {
    val totalMinutes = (ms.coerceAtLeast(0L) + 59_999L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours == 0L) "${minutes}m" else "${hours}h ${minutes}m"
}
