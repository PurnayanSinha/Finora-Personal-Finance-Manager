package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthResult
import com.example.domain.IndianCurrencyFormatter
import com.example.domain.ThemePreference
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val generatedResetCode: String? = null,
    val resetEmailTarget: String = ""
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val activeUserId: StateFlow<String?> = authRepository.activeUserIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val fallbackTheme: StateFlow<ThemePreference> = authRepository.fallbackThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ThemePreference.SYSTEM)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun login(usernameOrEmail: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            when (val res = authRepository.login(usernameOrEmail, password)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState()
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun signInOrCreateWithGoogle(activityContext: Context) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isEmpty() || clientId == "MY_GOOGLE_WEB_CLIENT_ID") {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Google OAuth Web Client ID is not configured yet. Set GOOGLE_WEB_CLIENT_ID in the Secrets panel in AI Studio (or enable Firebase setup) to sign in with Google."
            )
            return
        }

        val activity = activityContext as? Activity
        if (activity == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Unable to launch Google Sign-In from this context."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            try {
                val credentialManager = CredentialManager.create(activity)
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()

                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    when (
                        val res = authRepository.signInOrCreateWithGoogle(
                            email = googleCred.id,
                            displayName = googleCred.displayName,
                            googleSubjectId = googleCred.id,
                            profilePictureUrl = googleCred.profilePictureUri?.toString()
                        )
                    ) {
                        is AuthResult.Success -> _uiState.value = AuthUiState()
                        is AuthResult.Error -> _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = res.message
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Unexpected Google credential type received."
                    )
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: NoCredentialException) {
                Log.w("Auth", "No Google credentials available: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "No Google account found on this device or OAuth client SHA-1 is not registered."
                )
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Google Sign-In failed."
                )
            }
        }
    }

    fun register(
        fullName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        openingBalanceInput: String
    ) {
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passwords do not match.")
            return
        }
        val openingPaise = if (openingBalanceInput.isBlank()) {
            0L
        } else {
            IndianCurrencyFormatter.parseRupeesToPaise(openingBalanceInput, allowZero = true)
                ?: run {
                    _uiState.value = _uiState.value.copy(errorMessage = "Please enter a valid non-negative opening balance.")
                    return
                }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            when (val res = authRepository.register(fullName, username, email, password, openingPaise)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState()
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            when (val res = authRepository.requestPasswordReset(email)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        generatedResetCode = res.data,
                        resetEmailTarget = email.trim(),
                        infoMessage = "Verification code generated for ${email.trim()}. Enter code ${res.data} below to reset your password."
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun completePasswordReset(
        email: String,
        code: String,
        newPassword: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passwords do not match.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            when (val res = authRepository.completePasswordReset(email, code, newPassword)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState(infoMessage = "Password reset successfully. Please sign in.")
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository) as T
        }
    }
}
