package com.kuet.gymtest

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CameraAlt
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

@Composable
fun HomeScreen(
    profile: UserProfile?,
    onNavigate: (Tab) -> Unit,
    onStart: (Exercise) -> Unit
) {
    val pick = remember(profile) {
        profile?.let { RecommendationEngine.recommend(it).firstOrNull() }
    }

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
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Text("Welcome", color = DeepBlue.copy(alpha = 0.8f), fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ready to train?",
                color = DeepBlue,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Plan summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    if (profile == null) {
                        Text("Set up your profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tell us your goal, level and equipment to get a personalized plan.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        ActionButton("Set up profile") { onNavigate(Tab.Profile) }
                    } else {
                        Text("Your plan", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tag(profile.goal)
                            Tag(profile.fitnessLevel)
                            Tag(profile.equipment)
                        }
                        Spacer(Modifier.height(14.dp))
                        ActionButton("View full plan") { onNavigate(Tab.Plan) }
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
                        Text(
                            "TODAY'S PICK",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepBlue
                        )
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

            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                QuickCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CameraAlt,
                    label = "Camera"
                ) { onNavigate(Tab.Camera) }
                QuickCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Person,
                    label = "Profile"
                ) { onNavigate(Tab.Profile) }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LightGreen,
            contentColor = DeepGreen
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold)
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