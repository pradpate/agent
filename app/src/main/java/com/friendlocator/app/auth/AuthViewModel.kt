package com.friendlocator.app.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.friendlocator.app.data.models.User
import com.friendlocator.app.data.repository.UserRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: User? = null,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val googleSignInClient: GoogleSignInClient,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val result = userRepository.getUserById(currentUser.uid)
                result.fold(
                    onSuccess = { user ->
                        _uiState.value = AuthUiState(
                            isAuthenticated = true,
                            user = user,
                            isLoading = false
                        )
                    },
                    onFailure = {
                        _uiState.value = AuthUiState(
                            isAuthenticated = true,
                            isLoading = false
                        )
                    }
                )
            }
        }
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    // Create or update user in Firestore
                    val user = User(
                        id = firebaseUser.uid,
                        email = firebaseUser.email?.lowercase() ?: "",
                        displayName = firebaseUser.displayName ?: "",
                        profilePictureUrl = firebaseUser.photoUrl?.toString() ?: ""
                    )
                    
                    val result = userRepository.createOrUpdateUser(user)
                    result.fold(
                        onSuccess = { savedUser ->
                            _uiState.value = AuthUiState(
                                isAuthenticated = true,
                                user = savedUser,
                                isLoading = false
                            )
                        },
                        onFailure = { e ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Failed to save user: ${e.message}"
                            )
                        }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Authentication failed"
                    )
                }
            } catch (e: ApiException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Google sign in failed: ${e.statusCode}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Sign in failed: ${e.message}"
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                auth.signOut()
                googleSignInClient.signOut().await()
                
                _uiState.value = AuthUiState(
                    isAuthenticated = false,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Sign out failed: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
