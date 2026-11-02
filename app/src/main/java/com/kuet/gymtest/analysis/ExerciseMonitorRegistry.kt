package com.kuet.gymtest.analysis

/** A factory must return a fresh, independently owned analyzer on every call. */
data class ExerciseMonitorDefinition(
    val exerciseName: String,
    val monitoringLabel: String,
    val setupGuidance: String,
    val createAnalyzer: () -> ExerciseAnalyzer
)

/** Exact catalog names only; unregistered exercises retain the explicitly labeled demo. */
object ExerciseMonitorRegistry {
    private val definitions = listOf(
        ExerciseMonitorDefinition(
            exerciseName = "Bodyweight Squat",
            monitoringLabel = "Side-view squat monitoring",
            setupGuidance = "Stand sideways with your head and feet visible",
            createAnalyzer = { SquatAnalyzer() }
        )
    ).associateBy { it.exerciseName }

    fun find(exerciseName: String): ExerciseMonitorDefinition? = definitions[exerciseName]
}
