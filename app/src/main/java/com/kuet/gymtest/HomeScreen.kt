package com.kuet.gymtest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    profile: UserProfile, plan: List<Exercise>, history: List<WorkoutRecord>,
    onNavigate: (Tab) -> Unit, onStart: (Exercise) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val today = DayUtils.startOfDay()
    val weekDays = DayUtils.weekDays()
    val doneDays = history.map { DayUtils.startOfDay(it.timestamp) }.toSet()
    val weekCount = history.count { DayUtils.startOfDay(it.timestamp) in weekDays }
    val completedToday = history.filter { DayUtils.startOfDay(it.timestamp) == today }.map { it.exerciseName }.toSet()
    val todayExercises = plan.take(3)
    val pick = plan.firstOrNull() ?: RecommendationEngine.recommend(profile).firstOrNull()
    val searchResults = remember(query) { filteredExercises(ExerciseFilters(), query) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = Space.Xl, end = Space.Xl, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.Lg)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.Xl, bottom = Space.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(DayUtils.greeting() + ",", color = MutedInk, style = MaterialTheme.typography.bodyLarge)
                        Text("Ready to move?", style = MaterialTheme.typography.headlineLarge)
                    }
                    GlassCard(padding = PaddingValues(Space.Xs), shape = CircleShape) {
                        IconButton(onClick = { onNavigate(Tab.Profile) }) {
                            Icon(Icons.Default.PersonOutline, "Open profile", tint = Ink)
                        }
                    }
                }
            }
            item { GlassSearchField(query, { query = it }, Modifier.fillMaxWidth()) }
            if (query.isNotBlank()) {
                item { SectionHeading("Search results (${searchResults.size})") }
                if (searchResults.isEmpty()) item {
                    GlassCard(Modifier.fillMaxWidth()) { Text("No exercises found. Try a muscle group or another name.") }
                }
                items(searchResults, key = { "search-${it.name}" }) { exercise ->
                    GlassExerciseRow(exercise, { onStart(exercise) })
                }
            } else {
                item {
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onNavigate(Tab.Profile) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("This week", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                                Text("$weekCount workouts", style = MaterialTheme.typography.headlineMedium)
                            }
                            Icon(Icons.Default.CalendarMonth, null, tint = Forest)
                        }
                        Spacer(Modifier.height(Space.Lg))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            weekDays.forEachIndexed { i, day ->
                                val done = day in doneDays
                                Column(
                                    Modifier.weight(1f).semantics {
                                        contentDescription = "${listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[i]}: ${if (done) "workout completed" else "no workout"}"
                                    },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(30.dp).background(if (done) Leaf else GlassSurface, CircleShape)
                                            .border(1.dp, if (day == today) Forest else WarmOutline.copy(alpha = .45f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { if (done) Icon(Icons.Default.Check, null, tint = Ink, modifier = Modifier.size(18.dp)) }
                                    Spacer(Modifier.height(Space.Sm))
                                    Text(listOf("M", "T", "W", "T", "F", "S", "S")[i], style = MaterialTheme.typography.bodySmall, color = MutedInk)
                                }
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
                        GlassStatCard(DayUtils.streak(doneDays).toString(), "Day streak", Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                        GlassStatCard(doneDays.size.toString(), "Active days", Icons.Default.CalendarToday, Modifier.weight(1f))
                        GlassStatCard(history.size.toString(), "Workouts", Icons.Default.FitnessCenter, Modifier.weight(1f))
                    }
                }
                item { SectionHeading("Today's workout", "Your plan") { onNavigate(Tab.Plan) } }
                if (todayExercises.isEmpty()) item {
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onNavigate(Tab.Plan) }) {
                        Text("Make it your own", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Space.Xs))
                        Text("Choose the movements you enjoy and build your plan.", color = MutedInk)
                        Spacer(Modifier.height(Space.Md))
                        WorkoutPrimaryButton("Build your plan", { onNavigate(Tab.Plan) }, icon = Icons.Default.Add)
                    }
                }
                items(todayExercises, key = { it.name }) { exercise ->
                    GlassExerciseRow(exercise, { onStart(exercise) }, completed = exercise.name in completedToday)
                }
                if (pick != null && plan.isEmpty()) item {
                    SectionHeading("Recommended for you")
                    Spacer(Modifier.height(Space.Sm))
                    GlassExerciseRow(pick, { onStart(pick) })
                }
                item {
                    GlassCard(Modifier.fillMaxWidth(), fill = ActionGreen.copy(alpha = .36f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Spa, null, tint = Forest, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(Space.Md))
                            Column {
                                Text("Consistency builds progress.", style = MaterialTheme.typography.titleMedium)
                                Text("Small steps. Stronger every day.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.Md)) {
                        TextButton(onClick = { onNavigate(Tab.Exercises) }, modifier = Modifier.weight(1f)) { Text("Explore all exercises") }
                        TextButton(onClick = { onNavigate(Tab.Profile) }, modifier = Modifier.weight(1f)) { Text("My progress") }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { if (pick != null) onStart(pick) else onNavigate(Tab.Exercises) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Space.Xl),
            shape = WorkoutShapes.Control,
            containerColor = ActionGreen, contentColor = Ink
        ) { Icon(Icons.Default.PlayArrow, "Quick start workout") }
    }
}
