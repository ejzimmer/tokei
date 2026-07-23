package com.ejzimmer.tokei.data

import java.util.Calendar

/**
 * How many times each timer has been freshly started today. Deliberately
 * in-memory only (a plain singleton, not persisted anywhere) -- resets when
 * the app process dies, same tradeoff the web version made on purpose.
 */
object RunCounts {
    private data class Entry(val dayKey: String, val count: Int)

    private val counts = mutableMapOf<String, Entry>()

    private fun todayKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    fun recordRun(timerId: String) {
        val today = todayKey()
        val existing = counts[timerId]
        counts[timerId] = if (existing != null && existing.dayKey == today) {
            existing.copy(count = existing.count + 1)
        } else {
            Entry(today, 1)
        }
    }

    fun getRunCount(timerId: String): Int {
        val existing = counts[timerId] ?: return 0
        return if (existing.dayKey == todayKey()) existing.count else 0
    }

    fun clear(timerId: String) {
        counts.remove(timerId)
    }
}
