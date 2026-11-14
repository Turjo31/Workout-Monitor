package com.kuet.gymtest.data

import com.kuet.gymtest.UserProfile
import com.kuet.gymtest.WorkoutRecord
import com.kuet.gymtest.WorkoutRecordKind
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

data class CloudData(val profile: UserProfile?, val planNames: List<String>, val history: List<WorkoutRecord>)

@Serializable
internal data class ProfileRow(
    @SerialName("user_id") val userId: String,
    val age: Int,
    val gender: String,
    val goal: String,
    @SerialName("fitness_level") val fitnessLevel: String,
    val equipment: String,
    @SerialName("plan_names") val planNames: List<String>,
    // No default on purpose: it is always sent, so clearing the name in the app also clears it in the cloud.
    @SerialName("display_name") val displayName: String?
)

@Serializable
internal data class RecordRow(
    @SerialName("user_id") val userId: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("performed_at") val performedAt: String,
    val kind: String,
    @SerialName("per_set_reps") val perSetReps: List<Int>? = null,
    @SerialName("target_sets") val targetSets: Int? = null,
    @SerialName("target_reps") val targetReps: Int? = null
)

/** Row-level security limits every query to the signed-in user, so no user filter is needed on reads. */
object CloudRepository {
    private val db get() = SupabaseProvider.client.postgrest
    private fun userId() = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: error("Not signed in")

    suspend fun load(): CloudData {
        val profileRow = db.from("profiles")
            .select(Columns.list("user_id", "age", "gender", "goal", "fitness_level", "equipment", "plan_names", "display_name"))
            .decodeSingleOrNull<ProfileRow>()
        val rows = db.from("workout_records")
            .select(Columns.list("user_id", "session_id", "exercise_name", "performed_at", "kind",
                "per_set_reps", "target_sets", "target_reps"))
            .decodeList<RecordRow>()
        val profile = profileRow?.let { UserProfile(it.age, it.gender, it.goal, it.fitnessLevel, it.equipment, it.displayName.orEmpty()) }
        return CloudData(profile, profileRow?.planNames.orEmpty(), rows.mapNotNull { it.toRecord() })
    }

    suspend fun saveProfile(profile: UserProfile, planNames: List<String>) {
        db.from("profiles").upsert(
            ProfileRow(userId(), profile.age, profile.gender, profile.goal, profile.fitnessLevel,
                profile.equipment, planNames, profile.name.trim().ifBlank { null })
        ) { onConflict = "user_id" }
    }

    /** Idempotent: a record already stored for the same session is left untouched. Legacy records are not uploaded. */
    suspend fun saveRecords(records: List<WorkoutRecord>) {
        val id = userId()
        val rows = records.filter { it.sessionId != null && it.kind != WorkoutRecordKind.LEGACY }.map {
            RecordRow(id, it.sessionId, it.exerciseName, Instant.ofEpochMilli(it.timestamp).toString(),
                it.kind.name, it.perSetReps, it.targetSets, it.targetReps)
        }
        if (rows.isEmpty()) return
        db.from("workout_records").upsert(rows) {
            onConflict = "user_id,session_id"
            ignoreDuplicates = true
        }
    }

    private fun RecordRow.toRecord(): WorkoutRecord? = runCatching {
        val time = OffsetDateTime.parse(performedAt).toInstant().toEpochMilli()
        when (val k = WorkoutRecordKind.valueOf(kind)) {
            WorkoutRecordKind.MEASURED -> WorkoutRecord(time, exerciseName, sessionId, k, perSetReps, targetSets, targetReps)
            else -> WorkoutRecord(time, exerciseName, sessionId, k)
        }
    }.getOrNull()
}