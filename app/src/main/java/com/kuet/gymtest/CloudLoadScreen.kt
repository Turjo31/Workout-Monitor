package com.kuet.gymtest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kuet.gymtest.ui.components.WorkoutPrimaryButton
import com.kuet.gymtest.ui.theme.MutedInk
import com.kuet.gymtest.ui.theme.Space

/** Shown after login until the account's profile, plan and history have loaded. */
@Composable
fun CloudLoadScreen(error: String?, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(Space.Xxl),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (error == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(Space.Lg))
            Text("Loading your workouts…", color = MutedInk)
        } else {
            Text("Couldn't load your account", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.Sm))
            Text(error, color = MutedInk, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.Xl))
            WorkoutPrimaryButton("Try again", onRetry, Modifier.fillMaxWidth())
            TextButton(onClick = onSignOut) { Text("Log out") }
        }
    }
}