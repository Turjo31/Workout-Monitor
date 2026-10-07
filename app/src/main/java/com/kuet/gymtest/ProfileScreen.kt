package com.kuet.gymtest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuet.gymtest.ui.theme.DeepBlue
import com.kuet.gymtest.ui.theme.DeepGreen
import com.kuet.gymtest.ui.theme.LightBlue
import com.kuet.gymtest.ui.theme.LightGreen

@Composable
fun ProfileScreen(
    initial: UserProfile?,
    onSubmit: (UserProfile) -> Unit
) {
    var age by remember { mutableStateOf(initial?.age?.toString() ?: "") }
    var gender by remember { mutableStateOf(initial?.gender ?: "Male") }
    var goal by remember { mutableStateOf(initial?.goal ?: "Muscle Gain") }
    var level by remember { mutableStateOf(initial?.fitnessLevel ?: "Beginner") }
    var equipment by remember { mutableStateOf(initial?.equipment ?: "No Equipment") }

    val ageValue = age.toIntOrNull()
    val ageValid = ageValue != null && ageValue in 10..100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightBlue)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                "Profile",
                color = DeepBlue,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tell us a bit about yourself",
                color = DeepBlue.copy(alpha = 0.8f),
                fontSize = 15.sp
            )
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                value = age,
                onValueChange = { age = it.filter(Char::isDigit).take(3) },
                label = { Text("Age") },
                supportingText = { Text("Between 10 and 100") },
                isError = age.isNotEmpty() && !ageValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OptionGroup("Gender", listOf("Male", "Female", "Other"), gender) { gender = it }
            OptionGroup("Fitness goal", listOf("Muscle Gain", "Weight Loss"), goal) { goal = it }
            OptionGroup("Fitness level", listOf("Beginner", "Intermediate", "Advanced"), level) { level = it }
            OptionGroup("Equipment", listOf("No Equipment", "Dumbbells"), equipment) { equipment = it }

            Button(
                onClick = { onSubmit(UserProfile(ageValue!!, gender, goal, level, equipment)) },
                enabled = ageValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LightGreen,
                    contentColor = DeepGreen,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("Save & get plan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}