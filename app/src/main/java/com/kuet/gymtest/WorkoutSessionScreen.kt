package com.kuet.gymtest

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val WorkoutBackground = Color(0xFF0B0F0E)
private val WorkoutSurface = Color(0xFF151A18)
private val WorkoutAccent = Color(0xFFB7F34A)
private val WorkoutText = Color(0xFFF4F7F3)
private val WorkoutSecondary = Color(0xFF9AA49F)

@Composable
fun WorkoutSessionScreen(
    exercise: Exercise,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {

    var currentSet by remember {
        mutableStateOf(1)
    }

    var reps by remember {
        mutableStateOf(0)
    }

    var isPaused by remember {
        mutableStateOf(false)
    }

    var showFinishDialog by remember {
        mutableStateOf(false)
    }

    val totalSets = exercise.defaultSets
    val targetReps = exercise.defaultReps

    val progress =
        (reps.toFloat() / targetReps.toFloat())
            .coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WorkoutBackground)
    ) {

        WorkoutCameraPreview(
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            WorkoutTopBar(
                exerciseName = exercise.name,
                currentSet = currentSet,
                totalSets = totalSets,
                onBack = onBack
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {

                WorkoutRepCard(
                    reps = reps,
                    targetReps = targetReps,
                    progress = progress
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                WorkoutStatusCard(
                    isPaused = isPaused,
                    exercise = exercise
                )

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                WorkoutControls(
                    isPaused = isPaused,
                    onPause = {
                        isPaused = !isPaused
                    },
                    onFinish = {
                        showFinishDialog = true
                    }
                )
            }
        }
    }

    /*
     * Temporary fake rep counter.
     *
     * This will later be replaced by MediaPipe pose detection.
     */
    LaunchedEffect(
        isPaused,
        currentSet
    ) {

        while (!isPaused && reps < targetReps) {

            delay(1500)

            reps++
        }

        if (!isPaused && reps >= targetReps) {

            if (currentSet < totalSets) {

                delay(1000)

                currentSet++
                reps = 0
            }
        }
    }

    if (showFinishDialog) {

        AlertDialog(
            onDismissRequest = {
                showFinishDialog = false
            },
            title = {
                Text(
                    text = "Finish workout?"
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to finish this workout?"
                )
            },
            confirmButton = {

                Button(
                    onClick = {
                        showFinishDialog = false
                        onFinish()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WorkoutAccent
                    )
                ) {

                    Text(
                        text = "Finish",
                        color = Color.Black
                    )
                }
            },
            dismissButton = {

                OutlinedButton(
                    onClick = {
                        showFinishDialog = false
                    }
                ) {

                    Text(
                        text = "Cancel"
                    )
                }
            }
        )
    }
}

@Composable
private fun WorkoutCameraPreview(
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType =
                PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val preview =
                Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider =
                            previewView.surfaceProvider
                    }

            val cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA

            try {

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )

            } catch (_: Exception) {
            }

        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = {
            previewView
        },
        modifier = modifier
    )
}

@Composable
private fun WorkoutTopBar(
    exerciseName: String,
    currentSet: Int,
    totalSets: Int,
    onBack: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 18.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Color.Black.copy(alpha = 0.65f)
                )
        ) {

            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Spacer(
            modifier = Modifier.size(12.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = exerciseName,
                color = WorkoutText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Set $currentSet of $totalSets",
                color = WorkoutSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun WorkoutRepCard(
    reps: Int,
    targetReps: Int,
    progress: Float
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Color.Black.copy(alpha = 0.78f)
            )
            .padding(20.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "REPS",
                    color = WorkoutSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text = reps.toString(),
                    color = WorkoutAccent,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "/ $targetReps",
                color = WorkoutSecondary,
                fontSize = 18.sp,
                modifier = Modifier.padding(
                    bottom = 10.dp
                )
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        LinearProgressIndicator(
            progress = {
                progress
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape),
            color = WorkoutAccent,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun WorkoutStatusCard(
    isPaused: Boolean,
    exercise: Exercise
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Color.Black.copy(alpha = 0.72f)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = if (isPaused) {
                Icons.Default.Pause
            } else {
                Icons.Default.CheckCircle
            },
            contentDescription = null,
            tint = if (isPaused) {
                Color(0xFFFFC857)
            } else {
                WorkoutAccent
            },
            modifier = Modifier.size(24.dp)
        )

        Spacer(
            modifier = Modifier.size(12.dp)
        )

        Column {

            Text(
                text = if (isPaused) {
                    "Workout paused"
                } else {
                    "Ready"
                },
                color = WorkoutText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Text(
                text = if (isPaused) {
                    "Resume when you're ready"
                } else {
                    "Perform ${exercise.name} with controlled movement"
                },
                color = WorkoutSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun WorkoutControls(
    isPaused: Boolean,
    onPause: () -> Unit,
    onFinish: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Button(
            onClick = onPause,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WorkoutSurface
            )
        ) {

            Icon(
                imageVector = if (isPaused) {
                    Icons.Default.PlayArrow
                } else {
                    Icons.Default.Pause
                },
                contentDescription = null,
                tint = WorkoutText
            )

            Spacer(
                modifier = Modifier.size(6.dp)
            )

            Text(
                text = if (isPaused) {
                    "Resume"
                } else {
                    "Pause"
                },
                color = WorkoutText
            )
        }

        Button(
            onClick = onFinish,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WorkoutAccent
            )
        ) {

            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = null,
                tint = Color.Black
            )

            Spacer(
                modifier = Modifier.size(6.dp)
            )

            Text(
                text = "Finish",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}