package com.kuet.gymtest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuet.gymtest.ui.theme.DeepBlue
import com.kuet.gymtest.ui.theme.DeepGreen
import com.kuet.gymtest.ui.theme.LightBlue
import com.kuet.gymtest.ui.theme.LightGreen

private const val TOTAL_STEPS = 5

private data class Choice(val value: String, val subtitle: String = "")

private val genderChoices = listOf(Choice("Male"), Choice("Female"), Choice("Other"))

private val goalChoices = listOf(
    Choice("Muscle Gain", "Build strength and size"),
    Choice("Weight Loss", "Burn calories and lose fat")
)

private val levelChoices = listOf(
    Choice("Beginner", "New to working out"),
    Choice("Intermediate", "You train regularly"),
    Choice("Advanced", "Experienced and consistent")
)

private val equipmentChoices = listOf(
    Choice("No Equipment", "Bodyweight only"),
    Choice("Dumbbells", "A pair of dumbbells at home")
)

@Composable
fun OnboardingScreen(onComplete: (UserProfile) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }

    val ageValue = age.toIntOrNull()
    val ageValid = ageValue != null && ageValue in 10..100

    val canContinue = when (step) {
        0 -> ageValid
        1 -> gender.isNotEmpty()
        2 -> goal.isNotEmpty()
        3 -> level.isNotEmpty()
        else -> equipment.isNotEmpty()
    }

    val progress by animateFloatAsState(
        targetValue = (step + 1) / TOTAL_STEPS.toFloat(),
        label = "progress"
    )

    BackHandler(enabled = step > 0) { step-- }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar: back + progress
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (step > 0) {
                    IconButton(onClick = { step-- }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DeepBlue)
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(CircleShape),
                color = DeepBlue,
                trackColor = LightBlue
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${step + 1}/$TOTAL_STEPS",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
        }

        // Step content
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "step"
        ) { s ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                when (s) {
                    0 -> {
                        StepHeader("How old are you?", "We use this to keep your plan safe and suitable.")
                        Spacer(Modifier.height(32.dp))
                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it.filter(Char::isDigit).take(3) },
                            placeholder = { Text("Age", fontSize = 24.sp) },
                            supportingText = { Text("Between 10 and 100") },
                            isError = age.isNotEmpty() && !ageValid,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            ),
                            shape = RoundedCornerShape(18.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    1 -> ChoiceStep(
                        "What's your gender?",
                        "Used only as a profile detail.",
                        genderChoices, gender
                    ) { gender = it }

                    2 -> ChoiceStep(
                        "What's your goal?",
                        "We'll pick exercises that match it.",
                        goalChoices, goal
                    ) { goal = it }

                    3 -> ChoiceStep(
                        "What's your fitness level?",
                        "Be honest, you can change it later.",
                        levelChoices, level
                    ) { level = it }

                    else -> ChoiceStep(
                        "What equipment do you have?",
                        "We'll only suggest what you can do.",
                        equipmentChoices, equipment
                    ) { equipment = it }
                }
            }
        }

        // Continue button
        Button(
            onClick = {
                if (step < TOTAL_STEPS - 1) {
                    step++
                } else {
                    onComplete(UserProfile(ageValue!!, gender, goal, level, equipment))
                }
            },
            enabled = canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LightGreen,
                contentColor = DeepGreen,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                if (step < TOTAL_STEPS - 1) "Continue" else "Get started",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DeepBlue)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChoiceStep(
    title: String,
    subtitle: String,
    choices: List<Choice>,
    selected: String,
    onSelect: (String) -> Unit
) {
    StepHeader(title, subtitle)
    Spacer(Modifier.height(28.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.forEach { choice ->
            ChoiceCard(
                choice = choice,
                isSelected = choice.value == selected,
                onClick = { onSelect(choice.value) }
            )
        }
    }
}

@Composable
private fun ChoiceCard(choice: Choice, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) LightBlue else MaterialTheme.colorScheme.surface)
            .border(
                width = 2.dp,
                color = if (isSelected) DeepBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                choice.value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) DeepBlue else MaterialTheme.colorScheme.onSurface
            )
            if (choice.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    choice.subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = DeepBlue,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}