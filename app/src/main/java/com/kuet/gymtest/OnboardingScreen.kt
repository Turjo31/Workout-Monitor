package com.kuet.gymtest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*

@Composable
fun OnboardingScreen(onComplete: (UserProfile) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var age by key("onboarding-age-wheel") { rememberSaveable { mutableIntStateOf(25) } }
    var gender by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf("") }
    val canContinue = when (step) {
        0 -> age in 10..100
        1 -> gender.isNotEmpty()
        2 -> goal.isNotEmpty()
        3 -> level.isNotEmpty()
        else -> equipment.isNotEmpty()
    }
    BackHandler(enabled = step > 0) { step-- }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(Space.Lg), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) IconButton(onClick = { step-- }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous step")
            } else Icon(Icons.Default.FitnessCenter, null, tint = Forest, modifier = Modifier.padding(Space.Md))
            Text("Workout-Monitor", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${step + 1} / 5", color = MutedInk, style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { (step + 1) / 5f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Xxl).height(5.dp),
            color = Forest, trackColor = ActionGreen.copy(alpha = .4f)
        )
        AnimatedContent(
            targetState = step, modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "Onboarding step"
        ) { current ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Xxl),
                verticalArrangement = Arrangement.spacedBy(Space.Lg)
            ) {
                Text("A LITTLE ABOUT YOU", color = Forest, style = MaterialTheme.typography.labelMedium)
                Text(when (current) {
                    0 -> "Your journey starts here."
                    1 -> "How do you identify?"
                    2 -> "What moves you?"
                    3 -> "Find your starting point."
                    else -> "Your space. Your equipment."
                }, style = MaterialTheme.typography.headlineLarge)
                Text(when (current) {
                    0 -> "First, your age. We'll use it to personalize your exercise options."
                    1 -> "This is a profile detail. It doesn't limit your exercise choices."
                    2 -> "Choose the goal you'd like to work toward."
                    3 -> "Choose the experience level that feels right. You can update it later."
                    else -> "We'll start with movements you can do with what you have."
                }, color = MutedInk)
                Spacer(Modifier.height(Space.Sm))
                if (current == 0) {
                    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.Xl)) {
                        AgeWheelPicker(initialAge = age, onAgeSelected = { age = it })
                    }
                } else {
                    val choices = when (current) {
                        1 -> listOf("Male" to "", "Female" to "", "Other" to "")
                        2 -> listOf("Muscle Gain" to "Build strength and muscle", "Weight Loss" to "Move more and build consistency")
                        3 -> listOf("Beginner" to "I'm getting started", "Intermediate" to "I train regularly", "Advanced" to "I'm experienced and consistent")
                        else -> listOf("No Equipment" to "Just me and a little space", "Dumbbells" to "A pair of weights, more possibilities")
                    }
                    val selected = when (current) { 1 -> gender; 2 -> goal; 3 -> level; else -> equipment }
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.Md)) {
                        choices.forEach { (value, subtitle) ->
                            val chosen = value == selected
                            val fill by animateColorAsState(
                                if (chosen) ActionGreen.copy(alpha = .5f) else GlassSurface, label = "Choice"
                            )
                            GlassCard(
                                Modifier.fillMaxWidth().selectable(chosen, role = Role.RadioButton) {
                                    when (current) { 1 -> gender = value; 2 -> goal = value; 3 -> level = value; else -> equipment = value }
                                },
                                fill = fill, border = if (chosen) Leaf else GlassBorder
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(value, style = MaterialTheme.typography.titleMedium)
                                        if (subtitle.isNotEmpty()) Text(subtitle, color = MutedInk,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (chosen) Icon(Icons.Default.CheckCircle, null, tint = Forest)
                                }
                            }
                        }
                    }
                }
            }
        }
        WorkoutPrimaryButton(
            if (step == 4) "Let's get moving" else "Continue",
            {
                if (step < 4) step++
                else onComplete(UserProfile(age, gender, goal, level, equipment))
            },
            Modifier.fillMaxWidth().padding(horizontal = Space.Xxl, vertical = Space.Lg), enabled = canContinue
        )
    }
}
