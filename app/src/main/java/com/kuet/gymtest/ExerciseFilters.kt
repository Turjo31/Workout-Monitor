package com.kuet.gymtest

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.listSaver

const val ALL_FILTER = "All"

@Immutable
data class ExerciseFilters(
    val goal: String = ALL_FILTER,
    val level: String = ALL_FILTER,
    val equipment: String = ALL_FILTER,
    val muscle: String = ALL_FILTER,
    val category: String = ALL_FILTER
) {
    val activeCount: Int get() = listOf(goal, level, equipment, muscle, category).count { it != ALL_FILTER }

    companion object {
        fun fromProfile(profile: UserProfile) = ExerciseFilters(
            goal = profile.goal, level = profile.fitnessLevel, equipment = profile.equipment
        )

        val Saver = listSaver<ExerciseFilters, String>(
            save = { listOf(it.goal, it.level, it.equipment, it.muscle, it.category) },
            restore = { ExerciseFilters(it[0], it[1], it[2], it[3], it[4]) }
        )
    }
}

/** Library uses exact facets; the plan picker retains the existing profile eligibility rules. */
fun filteredExercises(filters: ExerciseFilters, query: String, profile: UserProfile? = null): List<Exercise> {
    val source = if (profile == null) RecommendationEngine.getAllExercises().filter {
        (filters.goal == ALL_FILTER || it.goal == filters.goal) &&
            (filters.level == ALL_FILTER || it.level == filters.level) &&
            (filters.equipment == ALL_FILTER || it.equipment == filters.equipment) &&
            (filters.muscle == ALL_FILTER || it.muscleGroup == filters.muscle)
    } else RecommendationEngine.filterExercises(
        age = profile.age,
        goal = filters.goal.takeUnless { it == ALL_FILTER },
        maxLevel = filters.level.takeUnless { it == ALL_FILTER },
        equipment = filters.equipment.takeUnless { it == ALL_FILTER },
        muscleGroup = filters.muscle.takeUnless { it == ALL_FILTER }
    )
    val term = query.trim()
    return source.filter { exercise ->
        (filters.category == ALL_FILTER || exercise.category == filters.category) &&
            (term.isEmpty() || listOf(exercise.name, exercise.muscleGroup, exercise.level,
                exercise.equipment, exercise.category, exercise.goal).any { it.contains(term, ignoreCase = true) })
    }.sortedBy { it.name }
}
