package com.kuet.gymtest.analysis

import android.util.Log

/** A factory must return a fresh, independently owned analyzer on every call. */
data class ExerciseMonitorDefinition(
    val exerciseName: String,
    val monitoringLabel: String,
    val setupGuidance: String,
    val formIssueLabel: String,
    val summariesEnabled: Boolean,
    val createAnalyzer: () -> ExerciseAnalyzer
) {
    constructor(exerciseName: String, monitoringLabel: String, setupGuidance: String,
                createAnalyzer: () -> ExerciseAnalyzer) :
        this(exerciseName, monitoringLabel, setupGuidance, "Form needs attention", false, createAnalyzer)
}

/** Exact catalog names only; unregistered exercises retain the explicitly labeled demo. */
object ExerciseMonitorRegistry {
    private val definitions = listOf(
        ExerciseMonitorDefinition(
            exerciseName = "Bodyweight Squat",
            monitoringLabel = "Side-view squat monitoring",
            setupGuidance = "Stand sideways with your head and feet visible",
            createAnalyzer = { SquatAnalyzer() }
        ),
        ExerciseMonitorDefinition(
            exerciseName = "Push-up",
            monitoringLabel = "Side-view push-up monitoring",
            setupGuidance = "Place the phone at floor level to your side, in plank position with your whole body visible",
            createAnalyzer = { PushUpAnalyzer(diagnostic = { line ->
                // Keep Android logging outside the pure analyzer for local JVM tests.
                runCatching { Log.d("PushUpTrace", line) }
            }) }
        ),
        ExerciseMonitorDefinition(
            exerciseName = "Jumping Jacks",
            monitoringLabel = "Front-view jumping jack monitoring",
            setupGuidance = "Face the camera with your whole body visible and room above your raised hands",
            createAnalyzer = { JumpingJackAnalyzer(diagnostic = { line ->
                // The pure analyzer also runs in local JVM tests, where Android Log is a stub.
                runCatching { Log.d("JumpingJackTrace", line) }
            }) }
        ),
        ExerciseMonitorDefinition(
            exerciseName = "Biceps Curl",
            monitoringLabel = "Side-view single-arm biceps curl monitoring",
            setupGuidance = "Stand sideways with one working arm, shoulder, elbow and hand visible; start with your arm lowered",
            formIssueLabel = "Elbow moved",
            summariesEnabled = true,
            createAnalyzer = { BicepsCurlAnalyzer(diagnostic = { line ->
                runCatching { Log.d("BicepsCurlTrace", line) }
            }) }
        ),
        ExerciseMonitorDefinition(
            exerciseName = "Lateral Raise",
            monitoringLabel = "Front-view two-arm lateral raise monitoring",
            setupGuidance = "Face the camera with both shoulders, arms, hands and hips visible; start with your arms at your sides",
            formIssueLabel = "Arm position needs attention",
            summariesEnabled = true,
            createAnalyzer = { LateralRaiseAnalyzer(diagnostic = { line ->
                runCatching { Log.d("LateralRaiseTrace", line) }
            }) }
        )
    ).associateBy { it.exerciseName }

    fun find(exerciseName: String): ExerciseMonitorDefinition? = definitions[exerciseName]
}
