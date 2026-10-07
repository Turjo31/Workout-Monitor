package com.kuet.gymtest

data class Exercise(
    val name: String,
    val goal: String,
    val level: String,
    val equipment: String,
    val muscleGroup: String,
    val category: String,
    val description: String,
    val instructions: List<String>,
    val defaultSets: Int,
    val defaultReps: Int,
    val lowImpact: Boolean = false
)