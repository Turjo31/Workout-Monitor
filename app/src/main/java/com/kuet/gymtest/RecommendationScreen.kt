package com.kuet.gymtest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*

@Composable
fun RecommendationScreen(
    profile: UserProfile?, plan: List<Exercise>, onSetupProfile: () -> Unit,
    onTogglePlan: (Exercise) -> Unit, onStart: (Exercise) -> Unit
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = choosing) { choosing = false }
    when {
        profile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            WorkoutPrimaryButton("Set up profile", onSetupProfile)
        }
        choosing -> ExercisePicker(profile, plan, onTogglePlan) { choosing = false }
        else -> PlanOverview(profile, plan, { choosing = true }, onTogglePlan, onStart)
    }
}

@Composable
private fun PlanOverview(
    profile: UserProfile, plan: List<Exercise>, onAdd: () -> Unit,
    onRemove: (Exercise) -> Unit, onStart: (Exercise) -> Unit
) {
    var category by rememberSaveable { mutableStateOf(ALL_FILTER) }
    var detailsName by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = remember { listOf(ALL_FILTER) + RecommendationEngine.getAllExercises().map { it.category }.distinct().sorted() }
    val visible = plan.filter { category == ALL_FILTER || it.category == category }
    Column(Modifier.fillMaxSize()) {
        WorkoutHeader("Your plan", "${profile.fitnessLevel} · ${plan.size} selected exercises")
        // This action stays pinned above the scrolling plan, even after exercises are added.
        GlassCard(
            Modifier.fillMaxWidth().padding(horizontal = Space.Xl, vertical = Space.Sm),
            fill = ActionGreen.copy(alpha = .3f), onClick = onAdd
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(ActionGreen, WorkoutShapes.Control),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Forest) }
                Spacer(Modifier.width(Space.Md))
                Column(Modifier.weight(1f)) {
                    Text("Add exercises to your plan", style = MaterialTheme.typography.titleMedium)
                    Text("Browse and select your movements", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.Xl),
            horizontalArrangement = Arrangement.spacedBy(Space.Sm)
        ) {
            categories.forEach { value -> WorkoutFilterChip(value, value == category) { category = value } }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Space.Xl),
            verticalArrangement = Arrangement.spacedBy(Space.Md)
        ) {
            item { SectionHeading(if (plan.isEmpty()) "A plan that feels like you" else "Your movements") }
            if (visible.isEmpty()) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FitnessCenter, null, tint = Forest)
                    Spacer(Modifier.height(Space.Md))
                    Text(if (plan.isEmpty()) "Your next chapter starts here" else "No $category exercises yet",
                        style = MaterialTheme.typography.titleMedium)
                    Text(if (plan.isEmpty()) "Use the card above to choose exercises. Your plan is yours to build."
                        else "Choose another category or add more movements.", color = MutedInk)
                }
            }
            items(visible, key = { it.name }) { exercise ->
                GlassExerciseRow(
                    exercise, { detailsName = exercise.name },
                    trailing = {
                        Column {
                            IconButton(onClick = { onStart(exercise) }) {
                                Icon(Icons.Default.PlayArrow, "Start ${exercise.name}", tint = Forest)
                            }
                            IconButton(onClick = { onRemove(exercise) }) {
                                Icon(Icons.Default.DeleteOutline, "Remove ${exercise.name} from plan", tint = MutedInk)
                            }
                        }
                    }
                )
            }
        }
    }
    val details = plan.find { it.name == detailsName }
    if (details != null) ExerciseDetailsSheet(details, { detailsName = null }, { onStart(details) })
}

@Composable
private fun ExercisePicker(
    profile: UserProfile, plan: List<Exercise>, onToggle: (Exercise) -> Unit, onDone: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable(stateSaver = ExerciseFilters.Saver) {
        mutableStateOf(ExerciseFilters.fromProfile(profile))
    }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val visible = remember(query, filters, profile) { filteredExercises(filters, query, profile) }
    val grouped = visible.groupBy { it.name.first().uppercaseChar() }
    Column(Modifier.fillMaxSize()) {
        WorkoutHeader("Add exercises", "${plan.size} selected · starting with your profile", onBack = onDone)
        SearchFilterBar(query, { query = it }, filters.activeCount, { showFilters = true },
            Modifier.padding(horizontal = Space.Xl))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.Xl, vertical = Space.Md),
            verticalArrangement = Arrangement.spacedBy(Space.Sm)
        ) {
            if (visible.isEmpty()) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("No matching movements", style = MaterialTheme.typography.titleMedium)
                    Text("Try a different search or adjust your filters.", color = MutedInk)
                    TextButton(onClick = { showFilters = true }) { Text("Adjust filters") }
                }
            }
            grouped.forEach { (letter, exercises) ->
                item(key = "letter-$letter") {
                    Text(letter.toString(), color = MutedInk, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Space.Md))
                }
                items(exercises, key = { it.name }) { exercise ->
                    val selected = plan.any { it.name == exercise.name }
                    GlassCard(
                        Modifier.fillMaxWidth().toggleable(selected, role = Role.Checkbox) { onToggle(exercise) },
                        fill = if (selected) ActionGreen.copy(alpha = .42f) else GlassSurface,
                        border = if (selected) Leaf else GlassBorder,
                        shape = WorkoutShapes.Exercise,
                        padding = PaddingValues(Space.Md)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.Md)) {
                            ExerciseIcon(exercise)
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                                Text("${exercise.muscleGroup} · ${exercise.level}",
                                    style = MaterialTheme.typography.bodySmall, color = MutedInk)
                                Text("${exercise.defaultSets} sets · ${exercise.defaultReps} reps",
                                    style = MaterialTheme.typography.bodySmall, color = Forest)
                            }
                            Checkbox(selected, onCheckedChange = null)
                        }
                    }
                }
            }
        }
        WorkoutPrimaryButton(
            "Done · ${plan.size} selected", onDone,
            Modifier.fillMaxWidth().padding(horizontal = Space.Xl, vertical = Space.Md),
            icon = Icons.Default.Check
        )
    }
    if (showFilters) ExerciseFilterSheet(
        filters, { filters = it; showFilters = false }, { showFilters = false },
        profileDefaults = ExerciseFilters.fromProfile(profile)
    )
}
