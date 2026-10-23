package com.kuet.gymtest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuet.gymtest.ui.theme.*

@Composable
fun ProfileScreen(
    initial: UserProfile?,
    history: List<WorkoutRecord>,
    plan: List<Exercise>,
    onSubmit: (UserProfile) -> Unit
) {
    val profile = initial ?: return
    var editing by remember { mutableStateOf(false) }
    val workoutDays = remember(history) { history.map { DayUtils.startOfDay(it.timestamp) }.toSet() }
    val streak = remember(workoutDays) { DayUtils.streak(workoutDays) }
    val weekDays = remember { DayUtils.weekDays().toSet() }
    val weeklyWorkouts = history.count { DayUtils.startOfDay(it.timestamp) in weekDays }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        ProfileHero(profile, onEdit = { editing = !editing })

        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileStat(Modifier.weight(1f), history.size.toString(), "Workouts", Icons.Default.FitnessCenter)
                ProfileStat(Modifier.weight(1f), streak.toString(), "Day streak", Icons.Default.LocalFireDepartment)
                ProfileStat(Modifier.weight(1f), plan.size.toString(), "In plan", Icons.Default.CheckCircle)
            }

            if (editing) {
                EditProfileCard(profile) {
                    onSubmit(it)
                    editing = false
                }
            } else {
                SectionCard("Personal details", Icons.Default.Person) {
                    DetailRow("Age", "${profile.age} years")
                    HorizontalDivider()
                    DetailRow("Gender", profile.gender)
                }

                SectionCard("Training profile", Icons.Default.Tune) {
                    DetailRow("Primary goal", profile.goal)
                    HorizontalDivider()
                    DetailRow("Fitness level", profile.fitnessLevel)
                    HorizontalDivider()
                    DetailRow("Available equipment", profile.equipment)
                }
            }

            SectionCard("Weekly activity", Icons.Default.CalendarMonth) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$weeklyWorkouts",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepBlue
                    )
                    Text(
                        " / 3 workouts",
                        modifier = Modifier.padding(bottom = 5.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (weeklyWorkouts / 3f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = DeepGreen,
                    trackColor = LightGreen
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (weeklyWorkouts >= 3) "Weekly goal completed — great work!"
                    else "${3 - weeklyWorkouts} more to reach your weekly goal",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard("Workout settings", Icons.Default.Settings) {
                SettingRow(Icons.Default.CameraAlt, "Pose monitoring", "Camera overlay enabled")
                HorizontalDivider()
                SettingRow(Icons.Default.Speed, "Training level", profile.fitnessLevel)
                HorizontalDivider()
                SettingRow(Icons.Default.SportsGymnastics, "Exercise plan", "${plan.size} selected exercises")
            }

            Text(
                "Your profile details are used to personalize exercise recommendations.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ProfileHero(profile: UserProfile, onEdit: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(LightBlue).statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = DeepBlue, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Fitness profile", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DeepBlue)
                Text(
                    "${profile.fitnessLevel} • ${profile.goal}",
                    fontSize = 14.sp,
                    color = DeepBlue.copy(alpha = .75f)
                )
                Spacer(Modifier.height(5.dp))
                Surface(color = LightGreen, shape = CircleShape) {
                    Text(
                        "ACTIVE",
                        color = DeepGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            FilledTonalIconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit profile")
            }
        }
    }
}

@Composable
private fun ProfileStat(modifier: Modifier, value: String, label: String, icon: ImageVector) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color.White)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = DeepBlue, modifier = Modifier.size(20.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(LightBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = DeepBlue, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = DeepBlue)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = DeepGreen)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditProfileCard(profile: UserProfile, save: (UserProfile) -> Unit) {
    var age by remember(profile) { mutableStateOf(profile.age.toString()) }
    var gender by remember(profile) { mutableStateOf(profile.gender) }
    var goal by remember(profile) { mutableStateOf(profile.goal) }
    var level by remember(profile) { mutableStateOf(profile.fitnessLevel) }
    var equipment by remember(profile) { mutableStateOf(profile.equipment) }
    val ageValue = age.toIntOrNull()
    val valid = ageValue != null && ageValue in 10..100

    SectionCard("Edit fitness profile", Icons.Default.Edit) {
        OutlinedTextField(
            age, { age = it.filter(Char::isDigit).take(3) },
            label = { Text("Age") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = age.isNotEmpty() && !valid,
            modifier = Modifier.fillMaxWidth()
        )
        EditOptions("Gender", listOf("Male", "Female", "Other"), gender) { gender = it }
        EditOptions("Goal", listOf("Muscle Gain", "Weight Loss"), goal) { goal = it }
        EditOptions("Fitness level", listOf("Beginner", "Intermediate", "Advanced"), level) { level = it }
        EditOptions("Equipment", listOf("No Equipment", "Dumbbells"), equipment) { equipment = it }
        Button(
            onClick = { save(UserProfile(ageValue!!, gender, goal, level, equipment)) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepGreen),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Save changes", fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditOptions(label: String, values: List<String>, selected: String, select: (String) -> Unit) {
    Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        values.forEach { value ->
            FilterChip(selected == value, { select(value) }, { Text(value) })
        }
    }
}
