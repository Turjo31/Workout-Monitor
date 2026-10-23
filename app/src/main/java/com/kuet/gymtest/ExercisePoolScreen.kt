package com.kuet.gymtest

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuet.gymtest.ui.theme.DeepBlue
import com.kuet.gymtest.ui.theme.DeepGreen
import com.kuet.gymtest.ui.theme.LightBlue

private const val ALL_EXERCISES = "All"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePoolScreen(onStart: (Exercise) -> Unit) {
    val allExercises = remember { RecommendationEngine.getAllExercises() }
    val goals = remember(allExercises) {
        listOf(ALL_EXERCISES) + allExercises.map { it.goal }.distinct()
    }
    val levels = remember(allExercises) {
        listOf(ALL_EXERCISES) + allExercises.map { it.level }.distinct()
    }
    val equipmentOptions = remember(allExercises) {
        listOf(ALL_EXERCISES) + allExercises.map { it.equipment }.distinct()
    }
    val muscles = remember(allExercises) {
        listOf(ALL_EXERCISES) + allExercises.map { it.muscleGroup }.distinct().sorted()
    }
    val categories = remember(allExercises) {
        listOf(ALL_EXERCISES) + allExercises.map { it.category }.distinct().sorted()
    }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ALL_EXERCISES) }
    var selectedGoal by remember { mutableStateOf(ALL_EXERCISES) }
    var selectedLevel by remember { mutableStateOf(ALL_EXERCISES) }
    var selectedEquipment by remember { mutableStateOf(ALL_EXERCISES) }
    var selectedMuscle by remember { mutableStateOf(ALL_EXERCISES) }
    var showFilters by remember { mutableStateOf(false) }

    val visibleExercises = remember(
        allExercises, query, selectedCategory, selectedGoal,
        selectedLevel, selectedEquipment, selectedMuscle
    ) {
        allExercises.filter { exercise ->
            val matchesCategory = selectedCategory == ALL_EXERCISES ||
                    exercise.category == selectedCategory
            val matchesGoal = selectedGoal == ALL_EXERCISES || exercise.goal == selectedGoal
            val matchesLevel = selectedLevel == ALL_EXERCISES || exercise.level == selectedLevel
            val matchesEquipment = selectedEquipment == ALL_EXERCISES ||
                    exercise.equipment == selectedEquipment
            val matchesMuscle = selectedMuscle == ALL_EXERCISES ||
                    exercise.muscleGroup == selectedMuscle
            val matchesQuery = query.isBlank() || listOf(
                exercise.name,
                exercise.muscleGroup,
                exercise.category,
                exercise.level,
                exercise.equipment
            ).any { it.contains(query.trim(), ignoreCase = true) }
            matchesCategory && matchesGoal && matchesLevel && matchesEquipment &&
                    matchesMuscle && matchesQuery
        }.sortedBy { it.name }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightBlue)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Exercise library",
                color = DeepBlue,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose any exercise and start camera monitoring.",
                color = DeepBlue.copy(alpha = 0.78f),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Search",
                            maxLines = 1
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
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
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "${visibleExercises.size} exercises",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            items(visibleExercises, key = { it.name }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    onStart = { onStart(exercise) }
                )
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Filter exercises", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                PoolFilterRow("Goal", goals, selectedGoal) { selectedGoal = it }
                PoolFilterRow("Difficulty", levels, selectedLevel) { selectedLevel = it }
                PoolFilterRow("Equipment", equipmentOptions, selectedEquipment) {
                    selectedEquipment = it
                }
                PoolFilterRow("Muscle group", muscles, selectedMuscle) { selectedMuscle = it }
                PoolFilterRow("Category", categories, selectedCategory) { selectedCategory = it }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            selectedGoal = ALL_EXERCISES
                            selectedLevel = ALL_EXERCISES
                            selectedEquipment = ALL_EXERCISES
                            selectedMuscle = ALL_EXERCISES
                            selectedCategory = ALL_EXERCISES
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
private fun PoolFilterRow(
    label: String,
    values: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DeepBlue)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(value) }
            )
        }
    }
}
