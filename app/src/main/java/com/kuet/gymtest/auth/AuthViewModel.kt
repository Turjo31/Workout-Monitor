package com.kuet.gymtest.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuet.gymtest.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val email: String) : AuthState
}

data class AuthUi(val busy: Boolean = false, val message: String? = null, val info: String? = null)

/** Owns the Supabase session. Passwords are never stored by the app; Supabase persists the session token. */
class AuthViewModel : ViewModel() {
    private val configured = SupabaseProvider.isConfigured

    val state: StateFlow<AuthState> =
        if (!configured) MutableStateFlow<AuthState>(AuthState.SignedOut)
        else SupabaseProvider.client.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Initializing -> AuthState.Loading
                is SessionStatus.Authenticated -> AuthState.SignedIn(status.session.user?.email.orEmpty())
                else -> AuthState.SignedOut
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val mutableUi = MutableStateFlow(AuthUi())
    val ui = mutableUi.asStateFlow()

    fun clearMessages() { mutableUi.value = mutableUi.value.copy(message = null, info = null) }

    fun signIn(email: String, password: String) = submit {
        SupabaseProvider.client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    fun signUp(email: String, password: String) = submit {
        SupabaseProvider.client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        // With "Confirm email" on, sign-up creates the account but no session until the link is tapped.
        if (SupabaseProvider.client.auth.currentSessionOrNull() == null) {
            mutableUi.value = mutableUi.value.copy(info = "Check your email and tap the confirmation link, then log in.")
        }
    }

    fun signOut() {
        if (!configured) return
        viewModelScope.launch { runCatching { SupabaseProvider.client.auth.signOut() } }
    }

    private fun submit(block: suspend () -> Unit) {
        if (!configured) {
            mutableUi.value = AuthUi(message = "Supabase isn't configured. Add supabase.url and supabase.anonKey to local.properties.")
            return
        }
        if (mutableUi.value.busy) return
        mutableUi.value = AuthUi(busy = true)
        viewModelScope.launch {
            try {
                block()
                mutableUi.value = mutableUi.value.copy(busy = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableUi.value = AuthUi(message = friendly(e))
            }
        }
    }

    private fun friendly(e: Exception): String {
        val text = (e.message ?: "").lowercase()
        return when {
            "invalid login" in text -> "Wrong email or password."
            "not confirmed" in text -> "Please confirm your email first, then log in."
            "already registered" in text || "already been registered" in text -> "That email already has an account. Log in instead."
            "password" in text && ("weak" in text || "at least" in text || "short" in text) ->
                "Choose a longer password (at least 6 characters)."
            "rate limit" in text -> "Too many attempts. Wait a minute and try again."
            "unable to resolve" in text || "timeout" in text || "timed out" in text || "network" in text ->
                "No internet connection. Please try again."
            else -> "Something went wrong. ${e.message.orEmpty()}".trim()
        }
    }
}