package com.kuet.gymtest.analysis

/** Exercises with measured analysis. To add one: register its factory here and extend the `when`s below. */
object MeasuredExercises {
    private val factories: Map<String, () -> ExerciseAnalyzer> = mapOf(
        "Bodyweight Squat" to { SquatAnalyzer() },
        "Push-up" to { PushUpAnalyzer() }
    )

    fun supports(name: String) = name in factories
    fun create(name: String): ExerciseAnalyzer = (factories[name] ?: error("No measured analyzer for $name"))()
}

fun ExerciseAnalysisResult.withFeedback(f: Feedback): ExerciseAnalysisResult = when (this) {
    is SquatAnalysisResult -> copy(feedback = f)
    is PushUpAnalysisResult -> copy(feedback = f)
}


val ExerciseAnalysisResult.phaseName: String
    get() = when (this) {
        is SquatAnalysisResult -> phase.name
        is PushUpAnalysisResult -> phase.name
    }

val ExerciseAnalysisResult.isNotReady: Boolean get() = phaseName == "NOT_READY"

fun ExerciseAnalysisResult.statusLine(): String {
    val label = phaseName.lowercase().replace('_', ' ')
    return "$label · " + when (this) {
        is SquatAnalysisResult ->
            if (depth == DepthStatus.TARGET_REACHED) "Target bend reached" else "Depth estimate: above target"
        is PushUpAnalysisResult ->
            if (phase == PushUpPhase.BOTTOM || phase == PushUpPhase.ASCENDING) "Target depth reached"
            else "Depth estimate: above target"
    }
}