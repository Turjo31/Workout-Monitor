package com.kuet.gymtest

import androidx.compose.runtime.saveable.listSaver

// Preserve the existing local state through rotation and saved-instance restoration.
val ProfileStateSaver = listSaver<UserProfile?, Any>(
    save = { if (it == null) emptyList() else listOf(it.age, it.gender, it.goal, it.fitnessLevel, it.equipment) },
    restore = { if (it.isEmpty()) null else UserProfile(it[0] as Int, it[1] as String,
        it[2] as String, it[3] as String, it[4] as String) }
)

val HistoryStateSaver = listSaver<List<WorkoutRecord>, Any>(
    save = { WorkoutHistoryCodec.encode(it) },
    restore = WorkoutHistoryCodec::decode
)

val PlanNamesStateSaver = listSaver<List<String>, String>(
    save = { it },
    restore = { it.toList() }
)
