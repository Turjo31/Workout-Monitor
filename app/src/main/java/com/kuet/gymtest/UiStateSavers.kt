package com.kuet.gymtest

import androidx.compose.runtime.saveable.listSaver

// Preserve the existing local state through rotation and saved-instance restoration.
val ProfileStateSaver = listSaver<UserProfile?, Any>(
    save = { if (it == null) emptyList() else listOf(it.age, it.gender, it.goal, it.fitnessLevel, it.equipment) },
    restore = { if (it.isEmpty()) null else UserProfile(it[0] as Int, it[1] as String,
        it[2] as String, it[3] as String, it[4] as String) }
)

val HistoryStateSaver = listSaver<List<WorkoutRecord>, Any>(
    save = { records -> records.flatMap { listOf(it.timestamp, it.exerciseName) } },
    restore = { values -> values.chunked(2).map { WorkoutRecord(it[0] as Long, it[1] as String) } }
)

val PlanNamesStateSaver = listSaver<List<String>, String>(
    save = { it },
    restore = { it.toList() }
)
