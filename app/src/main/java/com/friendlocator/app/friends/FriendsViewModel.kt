package com.friendlocator.app.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.friendlocator.app.data.models.FriendRequest
import com.friendlocator.app.data.models.Friendship
import com.friendlocator.app.data.repository.AlertRepository
import com.friendlocator.app.data.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val isLoading: Boolean = false,
    val friends: List<Friendship> = emptyList(),
    val pendingRequests: List<FriendRequest> = emptyList(),
    val sentRequests: List<FriendRequest> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
        loadPendingRequests()
        loadSentRequests()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            friendRepository.getFriendsFlow()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = "Failed to load friends: ${e.message}")
                }
                .collect { friends ->
                    _uiState.value = _uiState.value.copy(friends = friends)
                }
        }
    }

    private fun loadPendingRequests() {
        viewModelScope.launch {
            friendRepository.getPendingRequestsFlow()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = "Failed to load requests: ${e.message}")
                }
                .collect { requests ->
                    _uiState.value = _uiState.value.copy(pendingRequests = requests)
                }
        }
    }

    private fun loadSentRequests() {
        viewModelScope.launch {
            friendRepository.getSentRequestsFlow()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = "Failed to load sent requests: ${e.message}")
                }
                .collect { requests ->
                    _uiState.value = _uiState.value.copy(sentRequests = requests)
                }
        }
    }

    fun sendFriendRequest(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter an email address")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = friendRepository.sendFriendRequest(email.trim())
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Friend request sent successfully!"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to send friend request"
                    )
                }
            )
        }
    }

    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = friendRepository.acceptFriendRequest(requestId)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Friend request accepted!"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to accept request"
                    )
                }
            )
        }
    }

    fun declineFriendRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = friendRepository.declineFriendRequest(requestId)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Friend request declined"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to decline request"
                    )
                }
            )
        }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = friendRepository.removeFriend(friendId)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Friend removed"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to remove friend"
                    )
                }
            )
        }
    }

    fun sendAlert(friendId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = alertRepository.sendAlert(friendId)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Alert sent!"
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to send alert"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
