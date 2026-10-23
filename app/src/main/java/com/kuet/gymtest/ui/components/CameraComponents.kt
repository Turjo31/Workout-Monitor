package com.kuet.gymtest.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.kuet.gymtest.ui.theme.*

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** White system icons while the immersive camera is visible, restored on exit. */
@Composable
fun CameraSystemBars() {
    val activity = LocalContext.current.activity()
    DisposableEffect(activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val oldStatus = controller?.isAppearanceLightStatusBars ?: true
        val oldNavigation = controller?.isAppearanceLightNavigationBars ?: true
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = oldStatus
            controller?.isAppearanceLightNavigationBars = oldNavigation
        }
    }
}

@Composable
fun CameraScrim() {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Color.Black.copy(alpha = .5f),
        .35f to Color.Transparent,
        .65f to Color.Transparent,
        1f to Color.Black.copy(alpha = .55f)
    )))
}

@Composable
fun GlassCameraStat(value: String, label: String, modifier: Modifier = Modifier) {
    GlassCard(modifier, fill = CameraGlass, border = Color.White.copy(alpha = .28f),
        shape = WorkoutShapes.Stat, padding = PaddingValues(Space.Lg)) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = CameraInk)
        Text(label, style = MaterialTheme.typography.bodySmall, color = CameraInk.copy(alpha = .86f))
    }
}

@Composable
fun CameraPermissionCard(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier, fill = Ivory.copy(alpha = .92f)) {
        Icon(Icons.Default.CameraAlt, null, tint = Forest)
        Spacer(Modifier.height(Space.Md))
        Text("A little room to move", style = MaterialTheme.typography.headlineSmall)
        Text("Allow camera access to see your movement and live pose overlay. Keep your full body in frame.",
            color = MutedInk)
        Spacer(Modifier.height(Space.Lg))
        WorkoutPrimaryButton("Allow camera", onRequest, Modifier.fillMaxWidth())
    }
}
