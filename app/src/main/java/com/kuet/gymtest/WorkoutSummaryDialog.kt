package com.kuet.gymtest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kuet.gymtest.session.SetFormCounts
import com.kuet.gymtest.session.SetAttemptCounts
import com.kuet.gymtest.ui.theme.CameraGlass
import com.kuet.gymtest.ui.theme.CameraInk

/** Shared presentation: each analyzer may supply a form assessment, or leave it unassessed. */
@Composable
fun WorkoutSummaryDialog(
    title: String,
    exerciseName: String,
    repsBySet: List<Int>,
    formBySet: List<SetFormCounts>,
    attemptsBySet: List<SetAttemptCounts>,
    targetReps: Int,
    formIssueLabel: String,
    onDismiss: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit
) {
    val total = repsBySet.sum()
    val good = formBySet.sumOf { it.good }
    val issue = formBySet.sumOf { it.needsAttention }
    val unknown = formBySet.sumOf { it.notAssessed }
    val incomplete = attemptsBySet.sumOf { it.incomplete }
    val interrupted = attemptsBySet.sumOf { it.trackingInterrupted }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exerciseName, style = MaterialTheme.typography.titleMedium)
                Text("$total counted ${if (total == 1) "rep" else "reps"}")
                if (repsBySet.size > 1) {
                    repsBySet.forEachIndexed { index, reps ->
                        val attempts = attemptsBySet[index]
                        Text("Set ${index + 1}: $reps / $targetReps reps · " +
                            "${attempts.incomplete} incomplete · ${attempts.trackingInterrupted} tracking interrupted",
                            style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { (reps.toFloat() / targetReps).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Text("$total / $targetReps target reps")
                    LinearProgressIndicator(
                        progress = { (total.toFloat() / targetReps).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider(color = CameraInk.copy(alpha = .3f))
                Text("Form assessment", style = MaterialTheme.typography.titleSmall)
                if (good + issue == 0) {
                    Text(if (total == 0) "No completed reps to assess"
                        else "Form was not assessed for these reps")
                } else {
                    Text("No tracked form issue: $good")
                    Text("$formIssueLabel: $issue")
                }
                if (unknown > 0 && good + issue > 0) Text("Not assessed: $unknown")
                if (good + issue > 0) Text("2D camera estimate; not a technique or safety rating.",
                    style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = CameraInk.copy(alpha = .3f))
                Text("Uncounted observed attempts", style = MaterialTheme.typography.titleSmall)
                Text("Incomplete attempt: $incomplete")
                Text("Tracking interrupted mid-attempt: $interrupted")
                Text("These are observed attempts, not confirmed missed repetitions.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onAction,
            colors = ButtonDefaults.textButtonColors(contentColor = CameraInk)) { Text(actionLabel) } },
        dismissButton = { TextButton(onClick = onDismiss,
            colors = ButtonDefaults.textButtonColors(contentColor = CameraInk)) { Text("Close") } },
        containerColor = CameraGlass.copy(alpha = .88f),
        titleContentColor = CameraInk,
        textContentColor = CameraInk
    )
}
