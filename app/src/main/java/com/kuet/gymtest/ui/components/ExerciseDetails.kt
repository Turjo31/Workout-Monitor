package com.kuet.gymtest.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kuet.gymtest.Exercise
import com.kuet.gymtest.ui.theme.*

@Composable
fun GlassExerciseCard(exercise: Exercise, onStart: () -> Unit) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    GlassExerciseRow(exercise, { showDetails = true })
    if (showDetails) ExerciseDetailsSheet(exercise, { showDetails = false }, onStart)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailsSheet(exercise: Exercise, onDismiss: () -> Unit, onStart: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = IvoryLight
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.Xl),
                verticalArrangement = Arrangement.spacedBy(Space.Lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExerciseIcon(exercise)
                    Spacer(Modifier.width(Space.Md))
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.headlineMedium)
                        Text("${exercise.muscleGroup} · ${exercise.category}", color = MutedInk)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
                    listOf(exercise.level, exercise.equipment,
                        "${exercise.defaultSets} sets · ${exercise.defaultReps} reps").forEach {
                        Surface(
                            color = ActionGreen.copy(alpha = .35f),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.padding(bottom = Space.Sm)
                        ) {
                            Text(it, color = Forest, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = Space.Md, vertical = Space.Sm))
                        }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("About this movement", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Space.Sm))
                    Text(exercise.description, color = MutedInk)
                }
                Text("How to do it", style = MaterialTheme.typography.titleLarge)
                exercise.instructions.forEachIndexed { index, step ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.Md)) {
                            Text((index + 1).toString(), color = Forest, style = MaterialTheme.typography.titleMedium)
                            Text(step, Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(Space.Sm))
            }
            WorkoutPrimaryButton(
                "Start camera workout", {
                    onDismiss()
                    onStart()
                },
                Modifier.fillMaxWidth().padding(Space.Lg), icon = Icons.Default.PlayArrow
            )
        }
    }
}
