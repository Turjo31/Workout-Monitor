package com.kuet.gymtest.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuet.gymtest.*
import com.kuet.gymtest.ui.theme.*

@Composable
fun SearchFilterBar(
    query: String, onQuery: (String) -> Unit, filterCount: Int, onFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Sm),
        verticalAlignment = Alignment.CenterVertically) {
        GlassSearchField(query, onQuery, Modifier.weight(1f), placeholder = "Search")
        OutlinedButton(
            onClick = onFilter, modifier = Modifier.heightIn(min = 56.dp),
            shape = WorkoutShapes.Search,
            contentPadding = PaddingValues(horizontal = Space.Md)
        ) {
            Icon(Icons.Default.Tune, null, Modifier.size(20.dp))
            Spacer(Modifier.width(Space.Xs))
            Text(if (filterCount == 0) "Filter" else "Filter ($filterCount)",
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseFilterSheet(
    filters: ExerciseFilters, onApply: (ExerciseFilters) -> Unit, onDismiss: () -> Unit,
    profileDefaults: ExerciseFilters? = null
) {
    var draft by rememberSaveable(filters, stateSaver = ExerciseFilters.Saver) { mutableStateOf(filters) }
    val all = remember { RecommendationEngine.getAllExercises() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = IvoryLight,
        shape = WorkoutShapes.Card
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).imePadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.Xl),
                verticalArrangement = Arrangement.spacedBy(Space.Lg)
            ) {
                Text("Find your next movement", style = MaterialTheme.typography.headlineSmall)
                Text("Choose the exercises that fit the way you train.",
                    style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                FilterGroup("Goal", all.map { it.goal }.distinct(), draft.goal) { draft = draft.copy(goal = it) }
                FilterGroup("Difficulty", listOf("Beginner", "Intermediate", "Advanced"), draft.level) {
                    draft = draft.copy(level = it)
                }
                FilterGroup("Muscle group", all.map { it.muscleGroup }.distinct().sorted(), draft.muscle) {
                    draft = draft.copy(muscle = it)
                }
                FilterGroup("Equipment", all.map { it.equipment }.distinct(), draft.equipment) {
                    draft = draft.copy(equipment = it)
                }
                FilterGroup("Category", all.map { it.category }.distinct().sorted(), draft.category) {
                    draft = draft.copy(category = it)
                }
                if (profileDefaults != null) TextButton(onClick = { draft = profileDefaults }) {
                    Text("Use my profile defaults")
                }
                Spacer(Modifier.height(Space.Sm))
            }
            Row(
                Modifier.fillMaxWidth().padding(Space.Lg),
                horizontalArrangement = Arrangement.spacedBy(Space.Md)
            ) {
                OutlinedButton(onClick = { draft = ExerciseFilters() },
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp), shape = WorkoutShapes.Control) {
                    Text("Clear all")
                }
                WorkoutPrimaryButton("Apply filters", { onApply(draft) }, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(label: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Space.Xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
            (listOf(ALL_FILTER) + values).forEach { value ->
                WorkoutFilterChip(value, value == selected) { onSelect(value) }
            }
        }
    }
}
