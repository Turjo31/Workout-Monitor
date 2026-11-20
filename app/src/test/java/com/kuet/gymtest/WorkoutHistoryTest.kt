package com.kuet.gymtest

import androidx.compose.runtime.saveable.SaverScope
import com.kuet.gymtest.session.SetAttemptCounts
import com.kuet.gymtest.session.SetFormCounts
import com.kuet.gymtest.session.WorkoutSummary
import org.junit.Assert.*
import org.junit.Test

class WorkoutHistoryTest {
    private fun measured(ledger: List<Int>) = WorkoutRecord.measured(WorkoutSummary("session-id", "Bodyweight Squat", 123L, ledger, 3, 12))
    @Test fun versionedRoundTripThroughActualComposeSaver() {
        val records = listOf(WorkoutRecord(1, "Old"), WorkoutRecord(2, "Push-up", kind = WorkoutRecordKind.DEMO), measured(listOf(12, 5)))
        val saved = with(HistoryStateSaver) { SaverScope { true }.save(records) }!!
        assertEquals(records, HistoryStateSaver.restore(saved))
    }
    @Test fun legacyAlternatingValuesRemainReadableWithoutInventedCounts() {
        val restored = WorkoutHistoryCodec.decode(listOf(123L, "Squat", 456L, "Push-up"))
        assertEquals(2, restored.size)
        assertEquals(WorkoutRecordKind.LEGACY, restored[0].kind)
        assertNull(restored[0].totalReps); assertEquals(456L, restored[1].timestamp)
    }
    @Test fun partialAndFullResultsRetainActualLedger() {
        val partial = measured(listOf(12, 5))
        assertEquals(17, partial.totalReps); assertEquals(1, partial.completedSets); assertEquals(false, partial.fullyCompleted)
        val full = measured(listOf(12, 12, 12)); assertEquals(true, full.fullyCompleted)
        assertEquals(partial, WorkoutHistoryCodec.decode(WorkoutHistoryCodec.encode(listOf(partial))).single())
    }
    @Test fun detailedSetAndAttemptSummaryRoundTrips() {
        val record = WorkoutRecord.measured(WorkoutSummary("detailed", "Push-up", 456L, listOf(2, 1), 3, 2,
            formBySet = listOf(SetFormCounts(good = 1, needsAttention = 1), SetFormCounts(notAssessed = 1)),
            attemptsBySet = listOf(SetAttemptCounts(incomplete = 1), SetAttemptCounts(trackingInterrupted = 2))))
        val restored = WorkoutHistoryCodec.decode(WorkoutHistoryCodec.encode(listOf(record))).single()
        assertEquals(record, restored)
        assertEquals(1, restored.formBySet.sumOf { it.good })
        assertEquals(2, restored.attemptsBySet.sumOf { it.trackingInterrupted })
    }
    @Test fun previousHistoryDefaultsToUnassessedWithoutInventingAttempts() {
        val old = listOf<Any>("workout-history-v2", 8, 123L, "Bodyweight Squat", "old-session",
            WorkoutRecordKind.MEASURED.name, 1, 2, 1, 2)
        val restored = WorkoutHistoryCodec.decode(old).single()
        assertEquals(listOf(SetFormCounts(notAssessed = 2)), restored.formBySet)
        assertEquals(listOf(SetAttemptCounts()), restored.attemptsBySet)
    }
    @Test fun duplicateFinishDoesNotDuplicateHistory() {
        val r = measured(listOf(1))
        assertEquals(listOf(r), WorkoutHistoryCodec.appendOnce(listOf(r), r.copy(timestamp = 999)))
    }
    @Test fun malformedAndUnknownPayloadsFailSafely() {
        assertTrue(WorkoutHistoryCodec.decode(listOf("unknown-version")).isEmpty())
        assertTrue(WorkoutHistoryCodec.decode(listOf("workout-history-v2", 100)).isEmpty())
        assertTrue(WorkoutHistoryCodec.decode(listOf(12L)).isEmpty())
    }
}
