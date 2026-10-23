package com.kuet.gymtest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuet.gymtest.ui.theme.*

private const val ALL = "All"

@Composable
fun RecommendationScreen(
    profile: UserProfile?, plan: List<Exercise>, onSetupProfile: () -> Unit,
    onTogglePlan: (Exercise) -> Unit, onStart: (Exercise) -> Unit
) {
    var choosing by remember { mutableStateOf(false) }
    when {
        profile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onSetupProfile) { Text("Set up profile") }
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
    Column(Modifier.fillMaxSize()) {
        Header(
            "Your plan",
            if (plan.isEmpty()) "Build a workout plan that fits you" else "${plan.size} exercises selected",
            listOf(profile.goal, profile.fitnessLevel, profile.equipment)
        )
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            AddCard(onAdd)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (plan.isEmpty()) item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Your plan is empty", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Select only the exercises you want in your plan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(plan, key = { it.name }) { exercise ->
                PlanItem(exercise, { onStart(exercise) }, { onRemove(exercise) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePicker(
    profile: UserProfile, plan: List<Exercise>, onToggle: (Exercise) -> Unit, onDone: () -> Unit
) {
    val all = remember { RecommendationEngine.getAllExercises() }
    val goals = remember(all) { listOf(ALL) + all.map { it.goal }.distinct() }
    val levels = remember(all) { listOf(ALL) + all.map { it.level }.distinct() }
    val equipmentValues = remember(all) { listOf(ALL) + all.map { it.equipment }.distinct() }
    val muscles = remember(all) { listOf(ALL) + all.map { it.muscleGroup }.distinct().sorted() }
    var goal by remember { mutableStateOf(profile.goal) }
    var level by remember { mutableStateOf(profile.fitnessLevel) }
    var equipment by remember { mutableStateOf(profile.equipment) }
    var muscle by remember { mutableStateOf(ALL) }
    var query by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    val visible = remember(profile.age, goal, level, equipment, muscle, query) {
        RecommendationEngine.filterExercises(
            profile.age,
            goal.takeUnless { it == ALL },
            level.takeUnless { it == ALL },
            equipment.takeUnless { it == ALL },
            muscle.takeUnless { it == ALL }
        ).filter { exercise ->
            query.isBlank() || exercise.name.contains(query.trim(), ignoreCase = true) ||
                    exercise.muscleGroup.contains(query.trim(), ignoreCase = true)
        }.sortedBy { it.name }
    }
    val grouped = visible.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Add exercises", color = DeepBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${plan.size} selected", color = DeepBlue.copy(alpha = .75f), fontSize = 13.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search here") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(28.dp)
            )
            OutlinedButton(
                onClick = { showFilters = true },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Filter", fontWeight = FontWeight.Bold)
            }
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
            ) {
                if (visible.isEmpty()) item {
                    Text(
                        "No exercises match these filters.",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                grouped.forEach { (letter, exercises) ->
                    item(key = "letter-$letter") {
                        Text(
                            letter.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                        )
                    }
                    items(exercises, key = { it.name }) { exercise ->
                        SelectableItem(
                            exercise,
                            plan.any { it.name == exercise.name },
                            { onToggle(exercise) }
                        )
                    }
                }
            }

            Button(
                onClick = onDone,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(16.dp).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
            ) {
                Text(
                    if (plan.isEmpty()) "Done" else "Add ${plan.size} exercises",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Filter exercises", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                FilterRow("Goal", goals, goal) { goal = it }
                FilterRow("Difficulty", levels, level) { level = it }
                FilterRow("Equipment", equipmentValues, equipment) { equipment = it }
                FilterRow("Muscle group", muscles, muscle) { muscle = it }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            goal = ALL; level = ALL; equipment = ALL; muscle = ALL
                        },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Clear all", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { showFilters = false },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
                    ) { Text("Apply", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(label: String, values: List<String>, selected: String, choose: (String) -> Unit) {
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepBlue)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { value ->
            FilterChip(selected == value, { choose(value) }, { Text(value) })
        }
    }
}

@Composable
private fun SelectableItem(exercise: Exercise, selected: Boolean, toggle: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = toggle).then(
            if (selected) Modifier.border(2.dp, DeepGreen, RoundedCornerShape(18.dp)) else Modifier
        ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) LightGreen.copy(alpha = .45f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Checkbox(selected, { toggle() })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${exercise.muscleGroup} • ${exercise.category}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${exercise.defaultSets} sets × ${exercise.defaultReps} • ${exercise.level} • ${exercise.equipment}",
                    fontSize = 12.sp, color = DeepBlue, modifier = Modifier.padding(top = 5.dp)
                )
                Text(
                    exercise.description, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun AddCard(onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .border(1.dp, DeepBlue.copy(alpha = .35f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LightBlue.copy(alpha = .45f))
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(LightBlue, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = null, tint = DeepBlue)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Add exercises to your plan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Browse and select exercises", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PlanItem(exercise: Exercise, start: () -> Unit, remove: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${exercise.defaultSets} sets × ${exercise.defaultReps} • ${exercise.muscleGroup}")
            }
            IconButton(onClick = remove) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove")
            }
            IconButton(onClick = start) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = DeepGreen)
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, tags: List<String>) {
    Column(
        Modifier.fillMaxWidth().background(LightBlue).statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Text(title, color = DeepBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = DeepBlue.copy(alpha = .78f), fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.forEach {
                Text(
                    it, color = DeepBlue, fontSize = 12.sp,
                    modifier = Modifier.background(Color.White.copy(alpha = .75f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/** Used by the separate complete exercise library. */
@Composable
fun ExerciseCard(exercise: Exercise, onStart: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.clickable { expanded = !expanded }.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${exercise.muscleGroup} • ${exercise.category}", fontSize = 13.sp)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            Text(
                "${exercise.defaultSets} sets × ${exercise.defaultReps} • ${exercise.level} • ${exercise.equipment}",
                fontSize = 12.sp, color = DeepBlue, modifier = Modifier.padding(top = 8.dp)
            )
            if (expanded) Column(Modifier.padding(top = 10.dp)) {
                Text(exercise.description)
                exercise.instructions.forEachIndexed { index, step -> Text("${index + 1}. $step") }
            }
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LightGreen, contentColor = DeepGreen)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Start")
            }
        }
    }
}
