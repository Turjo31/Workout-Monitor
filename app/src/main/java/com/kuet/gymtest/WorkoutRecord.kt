package com.kuet.gymtest

import java.util.Calendar
import com.kuet.gymtest.session.WorkoutSummary

enum class WorkoutRecordKind { LEGACY, DEMO, MEASURED }

data class WorkoutRecord(
    val timestamp: Long,
    val exerciseName: String,
    val sessionId: String? = null,
    val kind: WorkoutRecordKind = WorkoutRecordKind.LEGACY,
    val perSetReps: List<Int>? = null,
    val targetSets: Int? = null,
    val targetReps: Int? = null
) {
    init {
        if (kind == WorkoutRecordKind.MEASURED) {
            require(!sessionId.isNullOrBlank() && targetSets != null && targetSets > 0 && targetReps != null && targetReps > 0)
            require(perSetReps != null && perSetReps.size in 1..targetSets && perSetReps.all { it in 0..targetReps })
            require(perSetReps.dropLast(1).all { it == targetReps } && perSetReps.sum() > 0)
        } else require(perSetReps == null) { "Demo/legacy records must not invent actual reps" }
    }
    val totalReps: Int? get() = perSetReps?.sum()
    val completedSets: Int? get() = perSetReps?.count { it == targetReps }
    val fullyCompleted: Boolean? get() = if (kind == WorkoutRecordKind.MEASURED) completedSets == targetSets else null

    companion object {
        fun measured(summary: WorkoutSummary) = WorkoutRecord(summary.timestamp, summary.exerciseName,
            summary.sessionId, WorkoutRecordKind.MEASURED, summary.perSet, summary.targetSets, summary.targetReps)
    }
}

/** Versioned saved-instance state, NOT a durable database or permanent history store. */
object WorkoutHistoryCodec {
    private const val VERSION = "workout-history-v2"
    fun encode(records: List<WorkoutRecord>): List<Any> = buildList {
        add(VERSION)
        records.forEach { r ->
            val row = listOf<Any>(r.timestamp, r.exerciseName, r.sessionId.orEmpty(), r.kind.name,
                r.targetSets ?: 0, r.targetReps ?: 0, r.perSetReps?.size ?: 0) + r.perSetReps.orEmpty()
            add(row.size); addAll(row)
        }
    }
    fun decode(values: List<Any>): List<WorkoutRecord> {
        if (values.isEmpty()) return emptyList()
        if (values.first() is Long) return values.chunked(2).mapNotNull { pair ->
            val time = pair.getOrNull(0) as? Long
            val name = pair.getOrNull(1) as? String
            if (time != null && name != null) WorkoutRecord(time, name) else null
        }
        if (values.first() != VERSION) return emptyList()
        return buildList {
            var index = 1
            while (index < values.size) {
                val size = values[index++] as? Int ?: break
                if (size < 7 || size > values.size - index) break
                val row = values.subList(index, index + size)
                index += size
                runCatching {
                    val count = row[6] as Int
                    require(count >= 0 && count == size - 7)
                    val kind = WorkoutRecordKind.valueOf(row[3] as String)
                    WorkoutRecord(row[0] as Long, row[1] as String, (row[2] as String).ifEmpty { null }, kind,
                        if (kind == WorkoutRecordKind.MEASURED) row.drop(7).map { it as Int } else null,
                        (row[4] as Int).takeIf { it > 0 }, (row[5] as Int).takeIf { it > 0 })
                }.getOrNull()?.let(::add)
            }
        }
    }
    fun appendOnce(records: List<WorkoutRecord>, record: WorkoutRecord): List<WorkoutRecord> =
        if (record.sessionId != null && records.any { it.sessionId == record.sessionId }) records else records + record
}

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
