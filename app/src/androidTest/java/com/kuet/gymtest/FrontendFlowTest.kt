package com.kuet.gymtest

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kuet.gymtest.ui.components.AppBackground
import com.kuet.gymtest.ui.theme.GymTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FrontendFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun planStartsEmptyAndCheckboxSelectionReturnsToPlan() {
        var selected = emptyList<Exercise>()
        compose.setContent {
            var plan by remember { mutableStateOf(emptyList<Exercise>()) }
            GymTestTheme {
                AppBackground {
                    RecommendationScreen(
                        UserProfile(25, "Male", "Muscle Gain", "Beginner", "No Equipment"),
                        plan, {}, { exercise ->
                            plan = if (exercise in plan) plan - exercise else plan + exercise
                            selected = plan
                        }, {}
                    )
                }
            }
        }
        compose.onNodeWithText("Add exercises to your plan").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Search exercises").performTextInput("Bodyweight Squat")
        compose.onNodeWithContentDescription("Search exercises").performImeAction()
        // The search input contains the same text; target the actual checkbox row.
        compose.onNode(hasText("Bodyweight Squat") and isToggleable()).performClick().assertIsOn()
        compose.onNodeWithText("Done · 1 selected").performClick()
        compose.onNodeWithText("Your movements").assertIsDisplayed()
        compose.onNodeWithText("Bodyweight Squat").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, selected.size) }
    }

    @Test
    fun libraryFilterSheetAppliesCategoryAndCanReset() {
        compose.setContent {
            GymTestTheme { AppBackground { ExercisePoolScreen {} } }
        }
        compose.onNodeWithText("Filter").performClick()
        compose.onNodeWithText("Cardio").performScrollTo().performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNodeWithText("Filter (1)").assertIsDisplayed().performClick()
        compose.onNodeWithText("Clear all").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNodeWithText("Filter").assertIsDisplayed()
    }
}
