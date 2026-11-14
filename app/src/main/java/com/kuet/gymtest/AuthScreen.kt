package com.kuet.gymtest

import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuet.gymtest.auth.AuthViewModel
import com.kuet.gymtest.ui.components.*
import com.kuet.gymtest.ui.theme.*

/**
 * Brand mark shown above the login and sign-up forms.
 *
 * TODO(app icon): to use the real app icon, add the image as a normal drawable, for example
 * app/src/main/res/drawable/app_logo.png (or .webp / .xml vector), then replace the Icon(...) below with:
 *
 *     Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(56.dp))
 *
 * and add the imports androidx.compose.foundation.Image and androidx.compose.ui.res.painterResource.
 * Do not point painterResource at the launcher icons in mipmap-*; adaptive launcher icons do not load that way.
 * Keep the gradient Box as the tile behind it, or remove the background if the logo has its own.
 */
@Composable
private fun BrandMark() {
    Box(
        Modifier.size(88.dp).background(
            Brush.linearGradient(listOf(ActionGreen, Leaf.copy(alpha = .75f))), WorkoutShapes.Card
        ),
        contentAlignment = Alignment.Center
    ) {
        // CHANGE HERE: swap this placeholder icon for the app logo (see the note above).
        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Forest, modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Forest, focusedLabelColor = Forest, cursorColor = Forest,
    focusedLeadingIconColor = Forest, unfocusedLeadingIconColor = MutedInk,
    focusedContainerColor = Color.White.copy(alpha = .5f), unfocusedContainerColor = Color.White.copy(alpha = .3f),
    unfocusedBorderColor = WarmOutline.copy(alpha = .6f)
)

@Composable
private fun ModeToggle(creating: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = .35f), WorkoutShapes.Control).padding(Space.Xs)
    ) {
        listOf(false to "Log in", true to "Sign up").forEach { (signUp, label) ->
            val selected = creating == signUp
            val fill by animateColorAsState(if (selected) ActionGreen else Color.Transparent, label = "Auth mode")
            Box(
                Modifier.weight(1f).clip(WorkoutShapes.Control).background(fill)
                    .selectable(selected, role = Role.Tab) { onChange(signUp) }
                    .padding(vertical = Space.Md),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) Ink else MutedInk)
            }
        }
    }
}

@Composable
private fun MessageBox(text: String) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, WorkoutShapes.Control)
            .padding(Space.Md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Sm)
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var creating by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    // Deliberately not saveable: passwords never go into saved instance state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var emailTouched by remember { mutableStateOf(false) }
    val emailOk = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordOk = password.length >= 6
    val valid = emailOk && passwordOk && (!creating || (confirm.isNotEmpty() && confirm == password))
    val sentEmail = creating && ui.info != null
    val submit = {
        if (valid && !ui.busy) {
            focus.clearFocus()
            if (creating) viewModel.signUp(email, password) else viewModel.signIn(email, password)
        }
    }
    fun switchTo(signUp: Boolean) {
        creating = signUp
        confirm = ""
        viewModel.clearMessages()
    }

    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Xxl, vertical = Space.Xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()
            Spacer(Modifier.height(Space.Lg))
            Text("Workout-Monitor", style = MaterialTheme.typography.headlineLarge)
            AnimatedContent(
                targetState = when { sentEmail -> 2; creating -> 1; else -> 0 },
                transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "Auth subtitle"
            ) { mode ->
                Text(
                    when (mode) {
                        2 -> "Almost there."
                        1 -> "Create your account. Your workouts stay saved and ready on any device."
                        else -> "Welcome back. Log in to pick up where you left off."
                    },
                    color = MutedInk, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.Xs)
                )
            }
            Spacer(Modifier.height(Space.Xl))
            GlassCard(Modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(Space.Xl)) {
                AnimatedContent(targetState = sentEmail, transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "Auth card") { sent ->
                    if (sent) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).background(ActionGreen, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MarkEmailRead, null, tint = Forest, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(Space.Lg))
                        Text("Check your email", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(Space.Sm))
                        Text("We sent a confirmation link to ${email.trim()}. Tap it, then come back here and log in.",
                            color = MutedInk, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Space.Xl))
                        WorkoutPrimaryButton("Back to log in", { password = ""; switchTo(false) }, Modifier.fillMaxWidth())
                    } else Column(Modifier.fillMaxWidth()) {
                        ModeToggle(creating) { switchTo(it) }
                        Spacer(Modifier.height(Space.Lg))
                        OutlinedTextField(
                            value = email, onValueChange = { email = it; viewModel.clearMessages() },
                            label = { Text("Email") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            isError = emailTouched && !emailOk,
                            supportingText = { if (emailTouched && !emailOk) Text("Enter a valid email address") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                            colors = authFieldColors(), shape = WorkoutShapes.Control,
                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && email.isNotEmpty()) emailTouched = true }
                        )
                        Spacer(Modifier.height(Space.Sm))
                        OutlinedTextField(
                            value = password, onValueChange = { password = it; viewModel.clearMessages() },
                            label = { Text("Password") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { visible = !visible }) {
                                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        if (visible) "Hide password" else "Show password")
                                }
                            },
                            supportingText = { if (creating) Text("At least 6 characters", color = if (passwordOk) Forest else MutedInk) },
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                                imeAction = if (creating) ImeAction.Next else ImeAction.Done),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { submit() }),
                            colors = authFieldColors(), shape = WorkoutShapes.Control, modifier = Modifier.fillMaxWidth()
                        )
                        AnimatedVisibility(creating, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column {
                                Spacer(Modifier.height(Space.Sm))
                                OutlinedTextField(
                                    value = confirm, onValueChange = { confirm = it; viewModel.clearMessages() },
                                    label = { Text("Confirm password") }, singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    isError = confirm.isNotEmpty() && confirm != password,
                                    supportingText = { if (confirm.isNotEmpty() && confirm != password) Text("Passwords don't match") },
                                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submit() }),
                                    colors = authFieldColors(), shape = WorkoutShapes.Control, modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        ui.message?.let {
                            Spacer(Modifier.height(Space.Md))
                            MessageBox(it)
                        }
                        Spacer(Modifier.height(Space.Lg))
                        WorkoutPrimaryButton(
                            if (ui.busy) "Please wait…" else if (creating) "Create account" else "Log in",
                            { submit() }, Modifier.fillMaxWidth(), enabled = valid && !ui.busy
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.Lg))
            Text("Pose detection runs on your device. Camera frames are never uploaded.",
                color = MutedInk, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}