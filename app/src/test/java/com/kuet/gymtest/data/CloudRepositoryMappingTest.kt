package com.kuet.gymtest.data

import com.kuet.gymtest.WorkoutRecord
import com.kuet.gymtest.WorkoutRecordKind
import com.kuet.gymtest.session.SetAttemptCounts
import com.kuet.gymtest.session.SetFormCounts
import com.kuet.gymtest.session.WorkoutSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudRepositoryMappingTest {
    @Test
    fun measuredRecordRoundTripsAllPerSetSummaryColumns() {
        val record = WorkoutRecord.measured(WorkoutSummary("session", "Jumping Jacks", 1_000L,
            perSet = listOf(2, 1), targetSets = 3, targetReps = 2,
            formBySet = listOf(SetFormCounts(good = 2), SetFormCounts(needsAttention = 1)),
            attemptsBySet = listOf(SetAttemptCounts(incomplete = 1), SetAttemptCounts(trackingInterrupted = 1))))

        val row = record.toRow("user")

        assertEquals(listOf(2, 0), row.goodFormRepsBySet)
        assertEquals(listOf(0, 1), row.attentionRepsBySet)
        assertEquals(listOf(1, 0), row.incompleteAttemptsBySet)
        assertEquals(record, row.toRecord())
    }

    @Test
    fun oldMeasuredCloudRowLoadsAsUnassessedWithNoInventedAttempts() {
        val row = RecordRow("user", "old", "Bodyweight Squat", "1970-01-01T00:00:01Z",
            WorkoutRecordKind.MEASURED.name, listOf(2), 1, 2)

        val record = row.toRecord()!!

        assertEquals(listOf(SetFormCounts(notAssessed = 2)), record.formBySet)
        assertEquals(listOf(SetAttemptCounts()), record.attemptsBySet)
    }

    @Test
    fun demoRowsDoNotWriteMeasuredSummaryColumns() {
        val row = WorkoutRecord(1_000L, "Plank", "demo", WorkoutRecordKind.DEMO).toRow("user")

        assertNull(row.goodFormRepsBySet)
        assertNull(row.incompleteAttemptsBySet)
        assertEquals(WorkoutRecordKind.DEMO, row.toRecord()!!.kind)
    }
}
