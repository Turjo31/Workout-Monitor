package com.kuet.gymtest

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuet.gymtest.ui.theme.DeepBlue
import com.kuet.gymtest.ui.theme.DeepGreen
import com.kuet.gymtest.ui.theme.LightBlue
import com.kuet.gymtest.ui.theme.LightGreen

private val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun HomeScreen(
    profile: UserProfile,
    history: List<WorkoutRecord>,
    onNavigate: (Tab) -> Unit,
    onStart: (Exercise) -> Unit
) {
    val pick = remember(profile) { RecommendationEngine.recommend(profile).firstOrNull() }
    val greeting = remember { DayUtils.greeting() }
    val weekDays = remember { DayUtils.weekDays() }
    val today = remember { DayUtils.startOfDay() }
    val doneDays = remember(history) { history.map { DayUtils.startOfDay(it.timestamp) }.toSet() }
    val streak = remember(history) { DayUtils.streak(doneDays) }
    val weekCount = history.count { DayUtils.startOfDay(it.timestamp) in weekDays }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightBlue)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Text(greeting, color = DeepBlue.copy(alpha = 0.8f), fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Ready to train?", color = DeepBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), Icons.Default.LocalFireDepartment, streak.toString(), "Day streak")
                StatCard(Modifier.weight(1f), Icons.Default.CalendarMonth, weekCount.toString(), "This week")
                StatCard(Modifier.weight(1f), Icons.Default.FitnessCenter, history.size.toString(), "Total")
            }

            // Weekly tracker
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("This week", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekDays.forEachIndexed { index, day ->
                            DayDot(
                                label = dayLabels[index],
                                done = day in doneDays,
                                isToday = day == today
                            )
                        }
                    }
                }
            }

            // Today's pick
            if (pick != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("TODAY'S PICK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepBlue)
                        Spacer(Modifier.height(6.dp))
                        Text(pick.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${pick.defaultSets} sets × ${pick.defaultReps} • ${pick.muscleGroup}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { onStart(pick) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LightGreen,
                                contentColor = DeepGreen
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Start", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Plan summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Your plan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag(profile.goal)
                        Tag(profile.fitnessLevel)
                        Tag(profile.equipment)
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onNavigate(Tab.Plan) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightGreen,
                            contentColor = DeepGreen
                        )
                    ) {
                        Text("View full plan", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                QuickCard(Modifier.weight(1f), Icons.Default.CameraAlt, "Camera") { onNavigate(Tab.Camera) }
                QuickCard(Modifier.weight(1f), Icons.Default.Person, "Profile") { onNavigate(Tab.Profile) }
            }
        }
    }
}

@Composable
private fun DayDot(label: String, done: Boolean, isToday: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) DeepBlue else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (done) LightGreen else MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isToday) Modifier.border(2.dp, DeepBlue, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = DeepGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: ImageVector, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LightBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = DeepBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = DeepBlue,
        modifier = Modifier
            .clip(CircleShape)
            .background(LightBlue)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun QuickCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(LightBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = DeepBlue)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}