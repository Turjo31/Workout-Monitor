package com.kuet.gymtest.session

import com.kuet.gymtest.analysis.*
import java.util.UUID

enum class SessionStatus { SETUP, RUNNING, PAUSED, BETWEEN_SETS, COMPLETE, FINISHED }
data class WorkoutProgress(
    val sessionId: String, val exerciseName: String, val targetSets: Int, val targetReps: Int,
    val perSet: List<Int> = listOf(0), val status: SessionStatus = SessionStatus.SETUP,
    val analysisEpoch: Long = 0, val cameraEpoch: Long = 0
) {
    val currentSet get() = perSet.size
    val currentReps get() = perSet.last()
    val totalReps get() = perSet.sum()
    val completedSets get() = perSet.count { it == targetReps }
    val fullyCompleted get() = completedSets == targetSets
}
data class WorkoutSummary(
    val sessionId: String, val exerciseName: String, val timestamp: Long,
    val perSet: List<Int>, val targetSets: Int, val targetReps: Int
) {
    val totalReps get() = perSet.sum()
    val completedSets get() = perSet.count { it == targetReps }
    val fullyCompleted get() = completedSets == targetSets
}

/** Called by one serialized owner; only committed repetition events change the ledger. */
class WorkoutSessionController(exerciseName: String, sets: Int, reps: Int, restored: WorkoutProgress? = null) {
    init { require(sets > 0 && reps > 0) }
    var state = restored?.takeIf { it.exerciseName == exerciseName && it.targetSets == sets && it.targetReps == reps }
        ?.let { it.copy(status = if (it.status == SessionStatus.RUNNING) SessionStatus.PAUSED else it.status,
            analysisEpoch = it.analysisEpoch + 1, cameraEpoch = 0) }
        ?: WorkoutProgress(UUID.randomUUID().toString(), exerciseName, sets, reps)
        private set
    private var lastOrdinal = 0
    private var lastSequence = -1L
    private var lastTimestamp = -1L

    private fun epoch(status: SessionStatus) {
        state = state.copy(status = status, analysisEpoch = state.analysisEpoch + 1)
        lastOrdinal = 0; lastSequence = -1; lastTimestamp = -1
    }
    fun start() { if (state.status == SessionStatus.SETUP) epoch(SessionStatus.RUNNING) }
    fun pause() { if (state.status == SessionStatus.RUNNING) epoch(SessionStatus.PAUSED) }
    fun resume() { if (state.status == SessionStatus.PAUSED) epoch(SessionStatus.RUNNING) }
    fun interruptTracking() { if (state.status == SessionStatus.RUNNING) epoch(SessionStatus.RUNNING) }
    fun bindCamera(cameraEpoch: Long) { pause(); state = state.copy(cameraEpoch = cameraEpoch); epoch(state.status) }
    fun nextSet() {
        if (state.status != SessionStatus.BETWEEN_SETS) return
        state = state.copy(perSet = state.perSet + 0)
        epoch(SessionStatus.RUNNING)
    }
    fun reset() {
        state = WorkoutProgress(UUID.randomUUID().toString(), state.exerciseName, state.targetSets, state.targetReps,
            cameraEpoch = state.cameraEpoch, analysisEpoch = state.analysisEpoch + 1)
        lastOrdinal = 0; lastSequence = -1; lastTimestamp = -1
    }
    fun accepts(key: FrameKey) = state.status == SessionStatus.RUNNING && key.sessionId == state.sessionId &&
        key.analysisEpoch == state.analysisEpoch && key.cameraEpoch == state.cameraEpoch

    fun accept(result: ExerciseAnalysisResult): Boolean {
        if (!accepts(result.key) || result.key.sequence <= lastSequence || result.timestampMs <= lastTimestamp) return false
        lastSequence = result.key.sequence; lastTimestamp = result.timestampMs
        val event = result.repetition ?: return true
        if (!result.trackingValid || event.ordinal <= lastOrdinal || event.timestampMs != result.timestampMs) return false
        lastOrdinal = event.ordinal
        state = state.copy(perSet = state.perSet.dropLast(1) + (state.currentReps + 1))
        if (state.currentReps == state.targetReps) {
            epoch(if (state.currentSet == state.targetSets) SessionStatus.COMPLETE else SessionStatus.BETWEEN_SETS)
        }
        return true
    }
    fun summary(timestamp: Long) = WorkoutSummary(state.sessionId, state.exerciseName, timestamp,
        state.perSet.toList(), state.targetSets, state.targetReps)

    /** Null for an empty or already-finished session. Caller still exits the route. */
    fun finish(timestamp: Long): WorkoutSummary? {
        if (state.status == SessionStatus.FINISHED) return null
        val summary = summary(timestamp).takeIf { it.totalReps > 0 }
        epoch(SessionStatus.FINISHED)
        return summary
    }
}

/** Small versioned saved-instance payload; never stores partial motion, filters or pending events. */
object WorkoutProgressCodec {
    fun encode(p: WorkoutProgress): ArrayList<Any> = arrayListOf<Any>("workout-v1", p.sessionId, p.exerciseName,
        p.targetSets, p.targetReps, p.status.name, p.analysisEpoch, p.perSet.size).apply { addAll(p.perSet) }

    fun decode(values: List<Any>?): WorkoutProgress? = runCatching {
        require(values != null && values.size >= 9 && values[0] == "workout-v1")
        val sets = values[3] as Int; val reps = values[4] as Int; val size = values[7] as Int
        require(sets > 0 && reps > 0 && size in 1..sets && values.size == 8 + size)
        val ledger = values.drop(8).map { it as Int }
        require(ledger.all { it in 0..reps } && ledger.dropLast(1).all { it == reps })
        val status = SessionStatus.valueOf(values[5] as String)
        WorkoutProgress(values[1] as String, values[2] as String, sets, reps, ledger,
            if (status == SessionStatus.RUNNING) SessionStatus.PAUSED else status, values[6] as Long)
    }.getOrNull()
}
