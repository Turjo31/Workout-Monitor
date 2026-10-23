package com.kuet.gymtest

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.kuet.gymtest.ui.components.AgeWheelPicker
import com.kuet.gymtest.ui.components.AppBackground
import com.kuet.gymtest.ui.theme.GymTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AgeWheelPickerTest {
    @get:Rule val compose = createComposeRule()

    private fun selectAge(age: Int) {
        compose.onNodeWithContentDescription("Age").performSemanticsAction(SemanticsActions.SetProgress) { it(age.toFloat()) }
        compose.onNodeWithContentDescription("Age")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$age years old"))
    }

    @Test
    fun wheelSwipesAndSupportsBothRangeEndpointsAtLargeFontScale() {
        var selected = 25
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.6f)) {
                GymTestTheme { AppBackground { AgeWheelPicker(25, { selected = it }) } }
            }
        }
        compose.onNodeWithContentDescription("Age").performTouchInput { swipeUp(durationMillis = 500) }
        compose.runOnIdle { assertTrue("A swipe must update the selected age", selected > 25) }
        selectAge(10)
        compose.runOnIdle { assertEquals(10, selected) }
        selectAge(100)
        compose.runOnIdle { assertEquals(100, selected) }
    }

    @Test
    fun wheelRestoresItsSelection() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { GymTestTheme { AppBackground { AgeWheelPicker(25, {}) } } }
        selectAge(61)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Age")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "61 years old"))
    }

    @Test
    fun onboardingRetainsAgeOnBackAndSubmitsSelectedAge() {
        var completed: UserProfile? = null
        compose.setContent { GymTestTheme { AppBackground { OnboardingScreen { completed = it } } } }
        selectAge(42)
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithContentDescription("Previous step").performClick()
        compose.onNodeWithContentDescription("Age")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "42 years old"))
        compose.onNodeWithText("Continue").performClick()
        for (choice in listOf("Male", "Muscle Gain", "Beginner", "No Equipment")) {
            compose.onNodeWithText(choice).performScrollTo().performClick()
            compose.onNodeWithText(if (choice == "No Equipment") "Let's get moving" else "Continue").performClick()
        }
        compose.runOnIdle { assertEquals(42, completed?.age) }
    }
}
