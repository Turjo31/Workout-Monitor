package com.kuet.gymtest.analysis

/** Upright, unmirrored model crop. x/y are normalized; z is not used for 2D counting. */
data class PoseLandmark(
    val x: Float, val y: Float, val z: Float,
    val visibility: Float?, val presence: Float? = null
)

data class FrameKey(val sessionId: String, val cameraEpoch: Long, val analysisEpoch: Long, val sequence: Long)

data class PoseFrame(
    val key: FrameKey, val timestampMs: Long,
    val imageWidth: Int, val imageHeight: Int, val landmarks: List<PoseLandmark>
)

enum class BodySide(val shoulder: Int, val hip: Int, val knee: Int, val ankle: Int, val heel: Int, val toe: Int) {
    LEFT(11, 23, 25, 27, 29, 31), RIGHT(12, 24, 26, 28, 30, 32)
}

enum class Feedback(val text: String, val priority: Int) {
    NO_POSE("Pose unclear — adjust the camera or lighting", 1),
    FRAMING("Keep your head and feet visible", 2),
    MOVE_BACK("Move farther back", 2),
    SIDEWAYS("Stand sideways", 2),
    UNCERTAIN("Pose unclear — adjust the camera or lighting", 3),
    STAND("Stand tall to begin", 4),
    LOWER("Lower into the squat", 4),
    RETURN("Return to standing", 4),
    SHALLOW("Try lowering farther on the next repetition", 4),
    COUNTED("Repetition counted", 5)
}

enum class DiscontinuityReason { TRACKING, GAP, EPOCH, PAUSE, CAMERA, OVERFLOW }
data class RepetitionCompleted(val ordinal: Int, val timestampMs: Long)

sealed interface ExerciseAnalysisResult {
    val key: FrameKey
    val timestampMs: Long
    val repetitions: Int
    val repetition: RepetitionCompleted?
    val feedback: Feedback
    val trackingValid: Boolean
}

interface ExerciseAnalyzer {
    fun processFrame(pose: PoseFrame): ExerciseAnalysisResult
    fun invalidateTracking(reason: DiscontinuityReason)
    fun reset()
}
