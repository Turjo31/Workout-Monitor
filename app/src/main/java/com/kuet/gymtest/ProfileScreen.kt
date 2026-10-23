package com.kuet.gymtest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*
import java.text.DateFormat
import java.util.Date

private const val WEEKLY_WORKOUT_TARGET = 3

@Composable
fun ProfileScreen(
    initial: UserProfile?, history: List<WorkoutRecord>, plan: List<Exercise>,
    onSubmit: (UserProfile) -> Unit, onOpenPlan: () -> Unit, onCamera: () -> Unit
) {
    val profile = initial ?: return
    var editing by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showDataInfo by rememberSaveable { mutableStateOf(false) }
    val week = DayUtils.weekDays()
    val counts = week.map { day -> history.count { DayUtils.startOfDay(it.timestamp) == day } }
    val weeklyCount = counts.sum()
    val doneDays = history.map { DayUtils.startOfDay(it.timestamp) }.toSet()
    val streak = DayUtils.streak(doneDays)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.Xl, end = Space.Xl, bottom = Space.Xxl),
        verticalArrangement = Arrangement.spacedBy(Space.Lg)
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.Lg),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your profile", style = MaterialTheme.typography.headlineLarge)
                    Text("Keep going. You're building something.", color = MutedInk,
                        style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit profile") }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), onClick = { editing = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).background(ActionGreen.copy(alpha = .5f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PersonOutline, null, tint = Forest, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(Space.Lg))
                    Column(Modifier.weight(1f)) {
                        Text("Your fitness profile", style = MaterialTheme.typography.titleLarge)
                        Text("${profile.age} years · ${profile.gender}", color = MutedInk,
                            style = MaterialTheme.typography.bodyMedium)
                        Text(profile.fitnessLevel, color = Forest, style = MaterialTheme.typography.labelMedium)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MutedInk)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
                GlassStatCard(history.size.toString(), "Workouts", Icons.Default.FitnessCenter, Modifier.weight(1f))
                GlassStatCard(streak.toString(), "Day streak", Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                GlassStatCard(plan.size.toString(), "In your plan", Icons.Default.CheckCircleOutline, Modifier.weight(1f))
            }
        }
        item { SectionHeading("Progress", "History") { showHistory = true } }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Weekly activity", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("$weeklyCount/$WEEKLY_WORKOUT_TARGET", style = MaterialTheme.typography.titleLarge, color = Forest)
                }
                Text("Workouts completed each day", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Space.Lg))
                WeeklyActivity(week, counts)
                Spacer(Modifier.height(Space.Lg))
                LinearProgressIndicator(
                    progress = { (weeklyCount.toFloat() / WEEKLY_WORKOUT_TARGET).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Forest, trackColor = ActionGreen.copy(alpha = .45f)
                )
                Spacer(Modifier.height(Space.Sm))
                Text(if (weeklyCount >= WEEKLY_WORKOUT_TARGET) "Weekly goal reached. Well done!"
                    else "Working toward $WEEKLY_WORKOUT_TARGET workouts this week",
                    color = MutedInk, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SectionHeading("Training preferences", "Edit") { editing = true } }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                ProfileDetail("Primary goal", profile.goal)
                HorizontalDivider(color = WarmOutline.copy(alpha = .25f))
                ProfileDetail("Fitness level", profile.fitnessLevel)
                HorizontalDivider(color = WarmOutline.copy(alpha = .25f))
                ProfileDetail("Equipment", profile.equipment)
            }
        }
        item { SectionHeading("Milestones") }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (history.isEmpty()) Icons.Default.OutlinedFlag else Icons.Default.EmojiEvents,
                        null, tint = Forest, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(Space.Md))
                    Column(Modifier.weight(1f)) {
                        Text(if (streak >= 7) "7-day streak" else if (history.isNotEmpty()) "First workout complete" else "Your first workout awaits",
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (streak >= 7) "Seven days of showing up for yourself."
                            else if (history.isNotEmpty()) "Every session is a step forward."
                            else "Finish a session to start your journey.",
                            color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { SectionHeading("Your space") }
        item { ProfileAction(Icons.Default.Edit, "Edit profile", "Personal details and training preferences") { editing = true } }
        item { ProfileAction(Icons.Default.FitnessCenter, "Your workout plan", "${plan.size} selected exercises", onOpenPlan) }
        item { ProfileAction(Icons.Default.CameraAlt, "Pose preview", "Open the camera and live pose overlay", onCamera) }
        item { ProfileAction(Icons.Default.Info, "About your data", "How your profile and workouts are kept") { showDataInfo = true } }
        item { Text("Workout-Monitor · Move with intention", color = MutedInk,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = Space.Sm)) }
    }
    if (editing) EditProfileSheet(profile, { editing = false }) { onSubmit(it); editing = false }
    if (showHistory) HistorySheet(history) { showHistory = false }
    if (showDataInfo) AlertDialog(
        onDismissRequest = { showDataInfo = false },
        icon = { Icon(Icons.Default.PrivacyTip, null) },
        title = { Text("Your workout data") },
        text = { Text("Pose detection runs on your device. This app does not upload camera frames. Your profile, plan and workout history are currently held locally in the app session. They can be restored after rotation, but are not stored as a permanent account.") },
        confirmButton = { TextButton(onClick = { showDataInfo = false }) { Text("Got it") } },
        containerColor = IvoryLight
    )
}

@Composable
private fun ProfileDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.Md), horizontalArrangement = Arrangement.spacedBy(Space.Lg)) {
        Text(label, Modifier.weight(1f), color = MutedInk, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Ink)
    }
}

@Composable
private fun ProfileAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, shape = WorkoutShapes.Exercise) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Md)) {
            Icon(icon, null, tint = Forest)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MutedInk)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MutedInk)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(profile: UserProfile, onDismiss: () -> Unit, onSave: (UserProfile) -> Unit) {
    var age by rememberSaveable { mutableStateOf(profile.age.toString()) }
    var gender by rememberSaveable { mutableStateOf(profile.gender) }
    var goal by rememberSaveable { mutableStateOf(profile.goal) }
    var level by rememberSaveable { mutableStateOf(profile.fitnessLevel) }
    var equipment by rememberSaveable { mutableStateOf(profile.equipment) }
    val ageValue = age.toIntOrNull()
    val valid = ageValue != null && ageValue in 10..100
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = IvoryLight,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxHeight(.9f).imePadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.Xl),
                verticalArrangement = Arrangement.spacedBy(Space.Md)
            ) {
                Text("Make it personal", style = MaterialTheme.typography.headlineMedium)
                Text("Your preferences set the starting point for recommendations.", color = MutedInk)
                OutlinedTextField(
                    value = age, onValueChange = { age = it.filter(Char::isDigit).take(3) },
                    label = { Text("Age") }, supportingText = { Text("Enter an age from 10 to 100") },
                    singleLine = true, isError = age.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = WorkoutShapes.Control
                )
                ProfileChoices("Gender", listOf("Male", "Female", "Other"), gender) { gender = it }
                ProfileChoices("Fitness goal", listOf("Muscle Gain", "Weight Loss"), goal) { goal = it }
                ProfileChoices("Fitness level", listOf("Beginner", "Intermediate", "Advanced"), level) { level = it }
                ProfileChoices("Equipment", listOf("No Equipment", "Dumbbells"), equipment) { equipment = it }
                Text("Changing preferences may remove exercises from your plan if they no longer match.",
                    color = MutedInk, style = MaterialTheme.typography.bodySmall)
            }
            WorkoutPrimaryButton("Save changes", {
                ageValue?.takeIf { it in 10..100 }?.let { onSave(UserProfile(it, gender, goal, level, equipment)) }
            }, Modifier.fillMaxWidth().padding(Space.Lg), enabled = valid, icon = Icons.Default.Check)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileChoices(title: String, choices: List<String>, value: String, onSelect: (String) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
            choices.forEach { choice -> WorkoutFilterChip(choice, value == choice) { onSelect(choice) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(history: List<WorkoutRecord>, onDismiss: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = IvoryLight,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxHeight(.8f), contentPadding = PaddingValues(Space.Xl),
            verticalArrangement = Arrangement.spacedBy(Space.Md)) {
            item { Text("Workout history", style = MaterialTheme.typography.headlineMedium) }
            if (history.isEmpty()) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("A fresh start", style = MaterialTheme.typography.titleMedium)
                    Text("Your finished sessions will appear here.", color = MutedInk)
                }
            }
            items(history.sortedByDescending { it.timestamp }) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(it.exerciseName, style = MaterialTheme.typography.titleMedium)
                    Text(dateFormat.format(Date(it.timestamp)), color = MutedInk)
                }
            }
        }
    }
}
