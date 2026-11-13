package com.kuet.gymtest

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuet.gymtest.auth.AuthViewModel
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    // Deliberately not saveable: the password never goes into saved instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val valid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() && password.length >= 6
    val submit = {
        if (valid && !ui.busy) { if (creating) viewModel.signUp(email, password) else viewModel.signIn(email, password) }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(Space.Xxl),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Workout-Monitor", style = MaterialTheme.typography.headlineLarge)
        Text(if (creating) "Create your account to keep your workouts safe."
        else "Welcome back. Log in to continue.", color = MutedInk)
        Spacer(Modifier.height(Space.Xl))
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = email, onValueChange = { email = it; viewModel.clearMessages() },
                label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(), shape = WorkoutShapes.Control
            )
            Spacer(Modifier.height(Space.Md))
            OutlinedTextField(
                value = password, onValueChange = { password = it; viewModel.clearMessages() },
                label = { Text("Password") },
                supportingText = { if (creating) Text("At least 6 characters") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(), shape = WorkoutShapes.Control
            )
            ui.message?.let {
                Spacer(Modifier.height(Space.Md))
                Text(it, color = SoftError, style = MaterialTheme.typography.bodyMedium)
            }
            ui.info?.let {
                Spacer(Modifier.height(Space.Md))
                Text(it, color = Forest, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Space.Lg))
            WorkoutPrimaryButton(
                if (ui.busy) "Please wait…" else if (creating) "Create account" else "Log in",
                { submit() }, Modifier.fillMaxWidth(), enabled = valid && !ui.busy
            )
        }
        TextButton(onClick = { creating = !creating; viewModel.clearMessages() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (creating) "Have an account? Log in" else "New here? Create an account")
        }
    }
}