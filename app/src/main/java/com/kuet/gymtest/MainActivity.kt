package com.kuet.gymtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF0B0F0E)
private val SurfaceDark = Color(0xFF151A18)
private val SurfaceLight = Color(0xFF1D2421)
private val Accent = Color(0xFFB7F34A)
private val TextPrimary = Color(0xFFF4F7F3)
private val TextSecondary = Color(0xFF9AA49F)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FitMonitorApp()
        }
    }
}

@Composable
fun FitMonitorApp() {

    var profile by remember {
        mutableStateOf(
            UserProfile(
                age = 20,
                gender = "Male",
                goal = "Muscle Gain",
                fitnessLevel = "Beginner",
                equipment = "No Equipment"
            )
        )
    }

    var currentScreen by remember {
        mutableStateOf("home")
    }

    var selectedExercise by remember {
        mutableStateOf<Exercise?>(null)
    }

    var workoutExercise by remember {
        mutableStateOf<Exercise?>(null)
    }

    var showCamera by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {

        when {

            workoutExercise != null -> {

                WorkoutSessionScreen(
                    exercise = workoutExercise!!,
                    onBack = {
                        workoutExercise = null
                    },
                    onFinish = {
                        workoutExercise = null
                        currentScreen = "home"
                    }
                )
            }

            showCamera -> {

                CameraScreen(
                    onBack = {
                        showCamera = false
                    }
                )
            }

            selectedExercise != null -> {

                ExerciseDetailsScreen(
                    exercise = selectedExercise!!,
                    onBack = {
                        selectedExercise = null
                    },
                    onStartWorkout = {

                        workoutExercise = selectedExercise
                        selectedExercise = null
                    }
                )
            }

            currentScreen == "home" -> {

                HomeScreen(
                    profile = profile,

                    onStartWorkout = {

                        val recommendations =
                            RecommendationEngine.recommend(profile)

                        if (recommendations.isNotEmpty()) {

                            workoutExercise =
                                recommendations.first()

                        } else {

                            showCamera = true
                        }
                    },

                    onOpenExercise = {
                        selectedExercise = it
                    },

                    onOpenWorkouts = {
                        currentScreen = "workouts"
                    },

                    onOpenProfile = {
                        currentScreen = "profile"
                    }
                )
            }

            currentScreen == "workouts" -> {

                WorkoutListScreen(
                    profile = profile,

                    onBack = {
                        currentScreen = "home"
                    },

                    onOpenExercise = {
                        selectedExercise = it
                    },

                    onStartWorkout = { exercise ->

                        workoutExercise = exercise
                    },

                    onOpenProfile = {
                        currentScreen = "profile"
                    }
                )
            }

            currentScreen == "profile" -> {

                ProfileScreen(
                    profile = profile,

                    onBack = {
                        currentScreen = "home"
                    },

                    onSave = {
                        profile = it
                        currentScreen = "home"
                    }
                )
            }
        }
    }
}


/* ============================================================
   HOME SCREEN
   ============================================================ */

@Composable
fun HomeScreen(
    profile: UserProfile,
    onStartWorkout: () -> Unit,
    onOpenExercise: (Exercise) -> Unit,
    onOpenWorkouts: () -> Unit,
    onOpenProfile: () -> Unit
) {

    val recommendations =
        RecommendationEngine.recommend(profile)

    Scaffold(
        containerColor = Background,

        bottomBar = {

            BottomNavigation(
                selected = "home",
                onHome = {},
                onWorkouts = onOpenWorkouts,
                onProfile = onOpenProfile
            )
        }

    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),

            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            item {

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text = "FitMonitor",
                    color = Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = "Good to see you.",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Let's get your workout started.",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }

            item {

                TodayWorkoutCard(
                    profile = profile,
                    onStart = onStartWorkout
                )
            }

            item {

                CameraWorkoutCard(
                    onOpenCamera = onStartWorkout
                )
            }

            item {

                SectionTitle(
                    title = "Today's Progress",
                    action = "View workouts",
                    onClick = onOpenWorkouts
                )
            }

            item {

                ProgressRow()
            }

            item {

                SectionTitle(
                    title = "Recommended for You",
                    action = null,
                    onClick = {}
                )
            }

            if (recommendations.isEmpty()) {

                item {
                    EmptyRecommendationCard()
                }

            } else {

                items(
                    recommendations.take(4)
                ) { exercise ->

                    ExerciseCard(
                        exercise = exercise,
                        onClick = {
                            onOpenExercise(exercise)
                        }
                    )
                }
            }

            item {
                Spacer(
                    modifier = Modifier.height(20.dp)
                )
            }
        }
    }
}


/* ============================================================
   TODAY WORKOUT
   ============================================================ */

@Composable
fun TodayWorkoutCard(
    profile: UserProfile,
    onStart: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Accent
        )
    ) {

        Column(
            modifier = Modifier.padding(22.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Color.Black.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.width(14.dp)
                )

                Column {

                    Text(
                        text = "Today's Workout",
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            "${profile.fitnessLevel} • ${profile.goal}",
                        color = Color.Black.copy(alpha = 0.65f),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(20.dp)
            ) {

                WorkoutStat(
                    icon = Icons.Default.FitnessCenter,
                    value = "3",
                    label = "Exercises"
                )

                WorkoutStat(
                    icon = Icons.Default.Timer,
                    value = "20",
                    label = "Minutes"
                )

                WorkoutStat(
                    icon = Icons.Default.DirectionsRun,
                    value = "120",
                    label = "Calories"
                )
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),

                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black
                ),

                shape = RoundedCornerShape(14.dp)
            ) {

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    text = "Start Workout",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


@Composable
fun WorkoutStat(
    icon: ImageVector,
    value: String,
    label: String
) {

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )

        Spacer(
            modifier = Modifier.width(6.dp)
        )

        Column {

            Text(
                text = value,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Text(
                text = label,
                color = Color.Black.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}


/* ============================================================
   CAMERA CARD
   ============================================================ */

@Composable
fun CameraWorkoutCard(
    onOpenCamera: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onOpenCamera()
            },

        shape = RoundedCornerShape(20.dp),

        colors = CardDefaults.cardColors(
            containerColor = SurfaceLight
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Accent.copy(alpha = 0.15f)
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector =
                        Icons.Default.CameraAlt,

                    contentDescription =
                        "Camera",

                    tint = Accent,

                    modifier =
                        Modifier.size(28.dp)
                )
            }

            Spacer(
                modifier = Modifier.width(16.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Camera Workout",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        "Use your camera for exercise tracking",

                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Icon(
                imageVector =
                    Icons.Default.ArrowForward,

                contentDescription = null,

                tint = Accent
            )
        }
    }
}


/* ============================================================
   PROGRESS
   ============================================================ */

@Composable
fun ProgressRow() {

    Row(
        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        ProgressCard(
            modifier = Modifier.weight(1f),
            value = "0",
            label = "Workouts",
            icon = Icons.Default.FitnessCenter
        )

        ProgressCard(
            modifier = Modifier.weight(1f),
            value = "0",
            label = "Minutes",
            icon = Icons.Default.Timer
        )

        ProgressCard(
            modifier = Modifier.weight(1f),
            value = "0",
            label = "Days",
            icon = Icons.Default.CalendarMonth
        )
    }
}


@Composable
fun ProgressCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: ImageVector
) {

    Card(
        modifier = modifier,

        shape = RoundedCornerShape(18.dp),

        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark
        )
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(20.dp)
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = value,
                color = TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}


/* ============================================================
   SECTION TITLE
   ============================================================ */

@Composable
fun SectionTitle(
    title: String,
    action: String?,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,

            modifier =
                Modifier.weight(1f)
        )

        if (action != null) {

            Text(
                text = action,
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,

                modifier =
                    Modifier.clickable {
                        onClick()
                    }
            )
        }
    }
}


/* ============================================================
   EXERCISE CARD
   ============================================================ */

@Composable
fun ExerciseCard(
    exercise: Exercise,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },

        shape = RoundedCornerShape(20.dp),

        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark
        )
    ) {

        Row(
            modifier = Modifier.padding(18.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(
                        RoundedCornerShape(16.dp)
                    )
                    .background(
                        Accent.copy(alpha = 0.12f)
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector =
                        if (exercise.category == "Cardio") {
                            Icons.Default.DirectionsRun
                        } else {
                            Icons.Default.FitnessCenter
                        },

                    contentDescription = null,

                    tint = Accent,

                    modifier =
                        Modifier.size(25.dp)
                )
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = exercise.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        "${exercise.muscleGroup} • ${exercise.category}",

                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text =
                        "${exercise.defaultSets} sets × ${exercise.defaultReps} reps",

                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Icon(
                imageVector =
                    Icons.Default.ArrowForward,

                contentDescription = null,

                tint = TextSecondary
            )
        }
    }
}


/* ============================================================
   EMPTY RECOMMENDATION
   ============================================================ */

@Composable
fun EmptyRecommendationCard() {

    Card(
        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(20.dp),

        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Text(
                text = "No exact matches",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text =
                    "Try changing your fitness level or equipment in your profile.",

                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}


/* ============================================================
   WORKOUT LIST
   ============================================================ */

@Composable
fun WorkoutListScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    onOpenExercise: (Exercise) -> Unit,
    onStartWorkout: (Exercise) -> Unit,
    onOpenProfile: () -> Unit
) {

    val exercises =
        RecommendationEngine.recommend(profile)

    Scaffold(
        containerColor = Background,

        bottomBar = {

            BottomNavigation(
                selected = "workouts",
                onHome = onBack,
                onWorkouts = {},
                onProfile = onOpenProfile
            )
        }

    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),

            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {

            item {

                TopBar(
                    title = "Workouts",
                    onBack = onBack
                )
            }

            item {

                Text(
                    text =
                        "Your recommended exercises",

                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            if (exercises.isEmpty()) {

                item {
                    EmptyRecommendationCard()
                }

            } else {

                items(exercises) { exercise ->

                    ExerciseCard(
                        exercise = exercise,

                        onClick = {
                            onOpenExercise(exercise)
                        }
                    )
                }
            }

            item {

                if (exercises.isNotEmpty()) {

                    Button(
                        onClick = {
                            onStartWorkout(
                                exercises.first()
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(15.dp),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Accent
                            )
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.CameraAlt,

                            contentDescription =
                                null,

                            tint = Color.Black
                        )

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Text(
                            text =
                                "Start Camera Workout",

                            color = Color.Black,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            item {

                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )
            }
        }
    }
}


/* ============================================================
   EXERCISE DETAILS
   ============================================================ */

@Composable
fun ExerciseDetailsScreen(
    exercise: Exercise,
    onBack: () -> Unit,
    onStartWorkout: () -> Unit
) {

    Scaffold(
        containerColor = Background
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),

            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            item {

                TopBar(
                    title = exercise.name,
                    onBack = onBack
                )
            }

            item {

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(24.dp),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                SurfaceLight
                        )
                ) {

                    Column(
                        modifier =
                            Modifier.padding(22.dp)
                    ) {

                        Text(
                            text = exercise.name,
                            color = TextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        Text(
                            text = exercise.description,
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(18.dp)
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {

                            InfoChip(
                                exercise.muscleGroup
                            )

                            InfoChip(
                                exercise.category
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(18.dp)
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(25.dp)
                        ) {

                            DetailStat(
                                value =
                                    exercise.defaultSets.toString(),

                                label = "Sets"
                            )

                            DetailStat(
                                value =
                                    exercise.defaultReps.toString(),

                                label = "Reps"
                            )

                            DetailStat(
                                value =
                                    exercise.equipment,

                                label = "Equipment"
                            )
                        }
                    }
                }
            }

            item {

                Text(
                    text = "How to perform",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(
                exercise.instructions.indices.toList()
            ) { index ->

                InstructionItem(
                    number = index + 1,
                    instruction =
                        exercise.instructions[index]
                )
            }

            item {

                Button(
                    onClick = onStartWorkout,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),

                    shape =
                        RoundedCornerShape(16.dp),

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Accent
                        )
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.CameraAlt,

                        contentDescription =
                            null,

                        tint = Color.Black
                    )

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Text(
                        text =
                            "Start With Camera",

                        color = Color.Black,

                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }

            item {

                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )
            }
        }
    }
}


/* ============================================================
   DETAIL HELPERS
   ============================================================ */

@Composable
fun InfoChip(
    text: String
) {

    Box(
        modifier = Modifier
            .clip(
                RoundedCornerShape(10.dp)
            )
            .background(
                Accent.copy(alpha = 0.12f)
            )
            .padding(
                horizontal = 10.dp,
                vertical = 6.dp
            )
    ) {

        Text(
            text = text,
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


@Composable
fun DetailStat(
    value: String,
    label: String
) {

    Column {

        Text(
            text = value,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}


@Composable
fun InstructionItem(
    number: Int,
    instruction: String
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Accent.copy(alpha = 0.12f)
                ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = number.toString(),
                color = Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(
            modifier = Modifier.width(12.dp)
        )

        Text(
            text = instruction,
            color = TextSecondary,
            fontSize = 14.sp,
            modifier =
                Modifier.padding(top = 5.dp)
        )
    }
}


/* ============================================================
   PROFILE
   ============================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    onSave: (UserProfile) -> Unit
) {

    var age by remember {
        mutableStateOf(
            profile.age.toString()
        )
    }

    var gender by remember {
        mutableStateOf(profile.gender)
    }

    var goal by remember {
        mutableStateOf(profile.goal)
    }

    var fitnessLevel by remember {
        mutableStateOf(
            profile.fitnessLevel
        )
    }

    var equipment by remember {
        mutableStateOf(
            profile.equipment
        )
    }

    Scaffold(
        containerColor = Background
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),

            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {

            item {

                TopBar(
                    title = "Profile",
                    onBack = onBack
                )
            }

            item {

                Text(
                    text = "Personal Information",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            item {

                OutlinedTextField(
                    value = age,

                    onValueChange = {
                        age = it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    label = {
                        Text("Age")
                    },

                    singleLine = true
                )
            }

            item {

                SelectionSection(
                    title = "Gender",

                    options = listOf(
                        "Male",
                        "Female",
                        "Other"
                    ),

                    selected = gender,

                    onSelect = {
                        gender = it
                    }
                )
            }

            item {

                SelectionSection(
                    title = "Fitness Goal",

                    options = listOf(
                        "Muscle Gain",
                        "Weight Loss"
                    ),

                    selected = goal,

                    onSelect = {
                        goal = it
                    }
                )
            }

            item {

                SelectionSection(
                    title = "Fitness Level",

                    options = listOf(
                        "Beginner",
                        "Intermediate",
                        "Advanced"
                    ),

                    selected = fitnessLevel,

                    onSelect = {
                        fitnessLevel = it
                    }
                )
            }

            item {

                SelectionSection(
                    title =
                        "Available Equipment",

                    options = listOf(
                        "No Equipment",
                        "Dumbbells"
                    ),

                    selected = equipment,

                    onSelect = {
                        equipment = it
                    }
                )
            }

            item {

                Button(
                    onClick = {

                        val parsedAge =
                            age.toIntOrNull()

                        if (parsedAge != null) {

                            onSave(
                                UserProfile(
                                    age = parsedAge,
                                    gender = gender,
                                    goal = goal,
                                    fitnessLevel =
                                        fitnessLevel,
                                    equipment =
                                        equipment
                                )
                            )
                        }
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),

                    shape =
                        RoundedCornerShape(16.dp),

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Accent
                        )
                ) {

                    Text(
                        text = "Save Profile",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {

                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )
            }
        }
    }
}


@Composable
fun SelectionSection(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {

    Column {

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        options.forEach { option ->

            val isSelected =
                option == selected

            OutlinedButton(
                onClick = {
                    onSelect(option)
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),

                shape =
                    RoundedCornerShape(12.dp),

                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor =
                            if (isSelected) {
                                Accent.copy(
                                    alpha = 0.12f
                                )
                            } else {
                                Color.Transparent
                            }
                    )
            ) {

                Text(
                    text = option,

                    color =
                        if (isSelected) {
                            Accent
                        } else {
                            TextSecondary
                        },

                    fontWeight =
                        if (isSelected) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                )
            }
        }
    }
}


/* ============================================================
   BOTTOM NAVIGATION
   ============================================================ */

@Composable
fun BottomNavigation(
    selected: String,
    onHome: () -> Unit,
    onWorkouts: () -> Unit,
    onProfile: () -> Unit
) {

    NavigationBar(
        containerColor = SurfaceDark,
        modifier =
            Modifier.navigationBarsPadding()
    ) {

        NavigationBarItem(
            selected =
                selected == "home",

            onClick = onHome,

            icon = {

                Icon(
                    imageVector =
                        Icons.Default.Home,

                    contentDescription =
                        "Home"
                )
            },

            label = {
                Text("Home")
            }
        )

        NavigationBarItem(
            selected =
                selected == "workouts",

            onClick = onWorkouts,

            icon = {

                Icon(
                    imageVector =
                        Icons.Default.FitnessCenter,

                    contentDescription =
                        "Workouts"
                )
            },

            label = {
                Text("Workouts")
            }
        )

        NavigationBarItem(
            selected =
                selected == "profile",

            onClick = onProfile,

            icon = {

                Icon(
                    imageVector =
                        Icons.Default.Person,

                    contentDescription =
                        "Profile"
                )
            },

            label = {
                Text("Profile")
            }
        )
    }
}


/* ============================================================
   TOP BAR
   ============================================================ */

@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 12.dp,
                bottom = 10.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        IconButton(
            onClick = onBack
        ) {

            Icon(
                imageVector =
                    Icons.Default.ArrowBack,

                contentDescription =
                    "Back",

                tint = TextPrimary
            )
        }

        Spacer(
            modifier =
                Modifier.width(4.dp)
        )

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}