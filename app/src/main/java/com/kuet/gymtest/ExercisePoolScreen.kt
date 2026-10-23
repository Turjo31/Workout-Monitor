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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuet.gymtest.ui.theme.DeepBlue
import com.kuet.gymtest.ui.theme.LightBlue

private const val ALL_EXERCISES = "All"

@Composable
fun ExercisePoolScreen(onStart: (Exercise) -> Unit) {
    val allExercises = remember { RecommendationEngine.getAllExercises() }
    val categories = remember(allExercises) {
        listOf(ALL_EXERCISES) + allExercises.map { it.category }.distinct().sorted()
    }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ALL_EXERCISES) }

    val visibleExercises = remember(allExercises, query, selectedCategory) {
        allExercises.filter { exercise ->
            val matchesCategory = selectedCategory == ALL_EXERCISES ||
                    exercise.category == selectedCategory
            val matchesQuery = query.isBlank() || listOf(
                exercise.name,
                exercise.muscleGroup,
                exercise.category,
                exercise.level,
                exercise.equipment
            ).any { it.contains(query.trim(), ignoreCase = true) }
            matchesCategory && matchesQuery
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search exercises") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
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
}
