package com.kuet.gymtest

import org.junit.Assert.*
import org.junit.Test

class ExerciseFiltersTest {
    @Test
    fun clearingFiltersRestoresTheCompleteCatalogue() {
        assertEquals(
            RecommendationEngine.getAllExercises().map { it.name }.toSet(),
            filteredExercises(ExerciseFilters(), "").map { it.name }.toSet()
        )
    }

    @Test
    fun searchAndMultipleFacetsCombine() {
        val results = filteredExercises(
            ExerciseFilters(goal = "Muscle Gain", level = "Beginner",
                equipment = "No Equipment", muscle = "Legs", category = "Strength"),
            "  SQUAT "
        )
        assertEquals(listOf("Bodyweight Squat"), results.map { it.name })
    }

    @Test
    fun profileDefaultsKeepTheExistingRecommendationEligibility() {
        val profile = UserProfile(55, "Female", "Muscle Gain", "Intermediate", "Dumbbells")
        val matches = filteredExercises(ExerciseFilters.fromProfile(profile), "", profile)
        assertEquals(
            RecommendationEngine.matchingExercises(profile).map { it.name }.toSet(),
            matches.map { it.name }.toSet()
        )
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.all { it.lowImpact })
        assertFalse(matches.any { it.level == "Advanced" })
    }

    @Test
    fun libraryDifficultyIsAnExactFilter() {
        val matches = filteredExercises(ExerciseFilters(level = "Advanced"), "")
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.all { it.level == "Advanced" })
    }

    @Test
    fun changingPlanFiltersCanExploreOutsideProfilePreferences() {
        val profile = UserProfile(25, "Male", "Muscle Gain", "Beginner", "No Equipment")
        val matches = filteredExercises(
            ExerciseFilters(goal = "Weight Loss", level = "Advanced", equipment = "Dumbbells"),
            "swing", profile
        )
        assertEquals(listOf("Dumbbell Swing"), matches.map { it.name })
    }

    @Test
    fun unmatchedSearchHasAnEmptyResult() {
        assertTrue(filteredExercises(ExerciseFilters(), "unknown exercise xyz").isEmpty())
    }
}
