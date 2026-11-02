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

/** Stable exercise-owned key; lower priority numbers preempt higher ones.
 * 1 tracking, 2 positioning, 3 uncertainty, 4 guidance, 5 completion, 6 encouragement.
 */
interface AnalysisFeedback {
    val key: String
    val text: String
    val priority: Int
}

/** Presentation only. Typed movement states and metrics remain inside each analyzer. */
data class AnalysisPresentation(
    val phaseKey: String,
    val phaseLabel: String,
    val ready: Boolean,
    val detail: String? = null
)

enum class DiscontinuityReason { TRACKING, GAP, EPOCH, PAUSE, CAMERA, OVERFLOW }
data class RepetitionCompleted(val ordinal: Int, val timestampMs: Long)

interface ExerciseAnalysisResult {
    val key: FrameKey
    val timestampMs: Long
    val repetitions: Int
    val repetition: RepetitionCompleted?
    val feedback: AnalysisFeedback
    val trackingValid: Boolean
    val presentation: AnalysisPresentation
}

interface ExerciseAnalyzer {
    fun processFrame(pose: PoseFrame): ExerciseAnalysisResult
    fun invalidateTracking(reason: DiscontinuityReason)
    fun reset()
}
