package com.kuet.gymtest

import java.util.Calendar

data class WorkoutRecord(
    val timestamp: Long,
    val exerciseName: String
)

object DayUtils {

    fun startOfDay(ms: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    fun addDays(day: Long, n: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = day }
        c.add(Calendar.DAY_OF_YEAR, n)
        return c.timeInMillis
    }

    /** Monday to Sunday of the current week, as start-of-day timestamps. */
    fun weekDays(): List<Long> {
        val today = startOfDay()
        val c = Calendar.getInstance().apply { timeInMillis = today }
        val back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val monday = addDays(today, -back)
        return (0..6).map { addDays(monday, it) }
    }

    fun streak(days: Set<Long>): Int {
        var cursor = startOfDay()
        if (cursor !in days) cursor = addDays(cursor, -1)
        var count = 0
        while (cursor in days) {
            count++
            cursor = addDays(cursor, -1)
        }
        return count
    }

    fun greeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}