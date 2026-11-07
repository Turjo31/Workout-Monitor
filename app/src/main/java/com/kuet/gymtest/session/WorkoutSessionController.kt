package com.kuet.gymtest.session

import com.kuet.gymtest.analysis.*
import java.util.UUID

enum class SessionStatus { SETUP, RUNNING, PAUSED, BETWEEN_SETS, COMPLETE, FINISHED }
data class SetFormCounts(val good: Int = 0, val needsAttention: Int = 0, val notAssessed: Int = 0) {
    val total get() = good + needsAttention + notAssessed
    fun add(quality: RepFormQuality?) = when (quality) {
        RepFormQuality.GOOD -> copy(good = good + 1)
        RepFormQuality.NEEDS_ATTENTION -> copy(needsAttention = needsAttention + 1)
        else -> copy(notAssessed = notAssessed + 1)
    }
}
data class SetAttemptCounts(val incomplete: Int = 0, val trackingInterrupted: Int = 0) {
    fun add(outcome: UncountedAttempt) = when (outcome) {
        UncountedAttempt.INCOMPLETE -> copy(incomplete = incomplete + 1)
        UncountedAttempt.TRACKING_INTERRUPTED -> copy(trackingInterrupted = trackingInterrupted + 1)
    }
}
data class WorkoutProgress(
    val sessionId: String, val exerciseName: String, val targetSets: Int, val targetReps: Int,
    val perSet: List<Int> = listOf(0), val status: SessionStatus = SessionStatus.SETUP,
    val analysisEpoch: Long = 0, val cameraEpoch: Long = 0,
    val formBySet: List<SetFormCounts> = listOf(SetFormCounts()),
    val attemptsBySet: List<SetAttemptCounts> = listOf(SetAttemptCounts())
) {
    val currentSet get() = perSet.size
    val currentReps get() = perSet.last()
    val totalReps get() = perSet.sum()
    val completedSets get() = perSet.count { it == targetReps }
    val fullyCompleted get() = completedSets == targetSets
}
data class WorkoutSummary(
    val sessionId: String, val exerciseName: String, val timestamp: Long,
    val perSet: List<Int>, val targetSets: Int, val targetReps: Int,
    val formBySet: List<SetFormCounts> = perSet.map { SetFormCounts(notAssessed = it) },
    val attemptsBySet: List<SetAttemptCounts> = perSet.map { SetAttemptCounts() }
) {
    val totalReps get() = perSet.sum()
    val completedSets get() = perSet.count { it == targetReps }
    val fullyCompleted get() = completedSets == targetSets
    val goodFormReps get() = formBySet.sumOf { it.good }
    val formIssueReps get() = formBySet.sumOf { it.needsAttention }
    val notAssessedReps get() = formBySet.sumOf { it.notAssessed }
    val incompleteAttempts get() = attemptsBySet.sumOf { it.incomplete }
    val trackingInterruptedAttempts get() = attemptsBySet.sumOf { it.trackingInterrupted }
}

/** Called by one serialized owner; repetition events and observed uncounted attempts use separate ledgers. */
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
    private var lastAttemptActive = false

    private fun epoch(status: SessionStatus) {
        state = state.copy(status = status, analysisEpoch = state.analysisEpoch + 1)
        lastOrdinal = 0; lastSequence = -1; lastTimestamp = -1; lastAttemptActive = false
    }
    fun start() { if (state.status == SessionStatus.SETUP) epoch(SessionStatus.RUNNING) }
    fun pause() { if (state.status == SessionStatus.RUNNING) epoch(SessionStatus.PAUSED) }
    fun resume() { if (state.status == SessionStatus.PAUSED) epoch(SessionStatus.RUNNING) }
    fun interruptTracking() {
        if (state.status == SessionStatus.RUNNING) {
            recordTrackingInterruption()
            epoch(SessionStatus.RUNNING)
        }
    }
    fun recordTrackingInterruption() {
        if (state.status == SessionStatus.RUNNING && lastAttemptActive) {
            recordAttempt(UncountedAttempt.TRACKING_INTERRUPTED)
            lastAttemptActive = false
        }
    }
    private fun recordAttempt(outcome: UncountedAttempt) {
        state = state.copy(attemptsBySet = state.attemptsBySet.dropLast(1) +
            state.attemptsBySet.last().add(outcome))
    }
    fun bindCamera(cameraEpoch: Long) { pause(); state = state.copy(cameraEpoch = cameraEpoch); epoch(state.status) }
    fun nextSet() {
        if (state.status != SessionStatus.BETWEEN_SETS) return
        state = state.copy(perSet = state.perSet + 0, formBySet = state.formBySet + SetFormCounts(),
            attemptsBySet = state.attemptsBySet + SetAttemptCounts())
        epoch(SessionStatus.RUNNING)
    }
    fun reset() {
        state = WorkoutProgress(UUID.randomUUID().toString(), state.exerciseName, state.targetSets, state.targetReps,
            cameraEpoch = state.cameraEpoch, analysisEpoch = state.analysisEpoch + 1)
        lastOrdinal = 0; lastSequence = -1; lastTimestamp = -1; lastAttemptActive = false
    }
    fun accepts(key: FrameKey) = state.status == SessionStatus.RUNNING && key.sessionId == state.sessionId &&
        key.analysisEpoch == state.analysisEpoch && key.cameraEpoch == state.cameraEpoch

    fun accept(result: ExerciseAnalysisResult): Boolean {
        if (!accepts(result.key) || result.key.sequence <= lastSequence || result.timestampMs <= lastTimestamp) return false
        lastSequence = result.key.sequence; lastTimestamp = result.timestampMs
        result.uncountedAttempt?.let(::recordAttempt)
        lastAttemptActive = result.attemptInProgress && result.trackingValid
        val event = result.repetition ?: return true
        if (!result.trackingValid || event.ordinal <= lastOrdinal || event.timestampMs != result.timestampMs) return false
        lastOrdinal = event.ordinal
        state = state.copy(perSet = state.perSet.dropLast(1) + (state.currentReps + 1),
            formBySet = state.formBySet.dropLast(1) + state.formBySet.last().add(event.assessment?.quality))
        if (state.currentReps == state.targetReps) {
            epoch(if (state.currentSet == state.targetSets) SessionStatus.COMPLETE else SessionStatus.BETWEEN_SETS)
        }
        return true
    }
    fun summary(timestamp: Long) = WorkoutSummary(state.sessionId, state.exerciseName, timestamp,
        state.perSet.toList(), state.targetSets, state.targetReps, state.formBySet.toList(),
        state.attemptsBySet.toList())

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
    fun encode(p: WorkoutProgress): ArrayList<Any> = arrayListOf<Any>("workout-v3", p.sessionId, p.exerciseName,
        p.targetSets, p.targetReps, p.status.name, p.analysisEpoch, p.perSet.size).apply {
        addAll(p.perSet)
        p.formBySet.forEach { add(it.good); add(it.needsAttention); add(it.notAssessed) }
        p.attemptsBySet.forEach { add(it.incomplete); add(it.trackingInterrupted) }
    }

    fun decode(values: List<Any>?): WorkoutProgress? = runCatching {
        require(values != null && values.size >= 9 && values[0] in listOf("workout-v1", "workout-v2", "workout-v3"))
        val v2 = values[0] != "workout-v1"
        val v3 = values[0] == "workout-v3"
        val sets = values[3] as Int; val reps = values[4] as Int; val size = values[7] as Int
        require(sets > 0 && reps > 0 && size in 1..sets &&
            values.size == 8 + size * (if (v3) 6 else if (v2) 4 else 1))
        val ledger = values.drop(8).take(size).map { it as Int }
        require(ledger.all { it in 0..reps } && ledger.dropLast(1).all { it == reps })
        val forms = if (v2) values.drop(8 + size).take(size * 3).chunked(3).map {
            SetFormCounts(it[0] as Int, it[1] as Int, it[2] as Int)
        } else ledger.map { SetFormCounts(notAssessed = it) }
        val attempts = if (v3) values.drop(8 + size * 4).chunked(2).map {
            SetAttemptCounts(it[0] as Int, it[1] as Int)
        } else ledger.map { SetAttemptCounts() }
        require(forms.size == size && forms.zip(ledger).all { (form, count) ->
            form.good >= 0 && form.needsAttention >= 0 && form.notAssessed >= 0 && form.total == count
        })
        require(attempts.size == size && attempts.all { it.incomplete >= 0 && it.trackingInterrupted >= 0 })
        val status = SessionStatus.valueOf(values[5] as String)
        WorkoutProgress(values[1] as String, values[2] as String, sets, reps, ledger,
            if (status == SessionStatus.RUNNING) SessionStatus.PAUSED else status, values[6] as Long,
            formBySet = forms, attemptsBySet = attempts)
    }.getOrNull()
}
