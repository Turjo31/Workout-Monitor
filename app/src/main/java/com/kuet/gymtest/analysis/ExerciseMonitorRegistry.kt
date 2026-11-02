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
            createAnalyzer = { SquatAnalyzer() } ),

            ExerciseMonitorDefinition(
                exerciseName = "Push-up",
            monitoringLabel = "Side-view push-up monitoring",
            setupGuidance = "Place the phone at floor level to your side, in plank position with your whole body visible",
            createAnalyzer = { PushUpAnalyzer() }
            )).associateBy { it.exerciseName }

    fun find(exerciseName: String): ExerciseMonitorDefinition? = definitions[exerciseName]
}
