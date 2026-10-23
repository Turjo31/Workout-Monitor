package com.kuet.gymtest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*

@Composable
fun ExercisePoolScreen(onStart: (Exercise) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable(stateSaver = ExerciseFilters.Saver) { mutableStateOf(ExerciseFilters()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val visible = remember(query, filters) { filteredExercises(filters, query) }
    Column(Modifier.fillMaxSize()) {
        WorkoutHeader("Exercise library", "Explore movements. Find your rhythm.")
        SearchFilterBar(query, { query = it }, filters.activeCount, { showFilters = true },
            Modifier.padding(horizontal = Space.Xl))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Space.Xl),
            verticalArrangement = Arrangement.spacedBy(Space.Md)
        ) {
            item {
                Text("${visible.size} exercises", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
            if (visible.isEmpty()) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Let's try another filter", style = MaterialTheme.typography.titleLarge)
                    Text("No movements match your current search.", color = MutedInk)
                    TextButton(onClick = { query = ""; filters = ExerciseFilters() }) { Text("Show all exercises") }
                }
            }
            items(visible, key = { it.name }) { exercise ->
                GlassExerciseCard(exercise) { onStart(exercise) }
            }
        }
    }
    if (showFilters) ExerciseFilterSheet(filters, {
        filters = it
        showFilters = false
    }, { showFilters = false })
}
