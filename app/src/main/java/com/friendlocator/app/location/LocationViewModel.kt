package com.friendlocator.app.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.friendlocator.app.data.models.Friendship
import com.friendlocator.app.data.models.UserLocation
import com.friendlocator.app.data.repository.FriendRepository
import com.friendlocator.app.data.repository.LocationRepository
import com.friendlocator.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationUiState(
    val isLoading: Boolean = false,
    val isLocationSharingEnabled: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasBackgroundPermission: Boolean = false,
    val currentLocation: UserLocation? = null,
    val friendLocations: List<FriendLocationInfo> = emptyList(),
    val error: String? = null
)

data class FriendLocationInfo(
    val friendship: Friendship,
    val location: UserLocation?
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val friendRepository: FriendRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private var friendIds: List<String> = emptyList()

    init {
        checkPermissions()
        loadCurrentLocation()
        loadFriendsAndLocations()
    }

    private fun checkPermissions() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _uiState.value = _uiState.value.copy(
            hasLocationPermission = hasFineLocation,
            hasBackgroundPermission = hasBackgroundLocation
        )
    }

    fun updatePermissions(hasForeground: Boolean, hasBackground: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasLocationPermission = hasForeground,
            hasBackgroundPermission = hasBackground
        )
    }

    private fun loadCurrentLocation() {
        viewModelScope.launch {
            locationRepository.getCurrentUserLocationFlow()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = "Failed to load location: ${e.message}")
                }
                .collect { location ->
                    _uiState.value = _uiState.value.copy(currentLocation = location)
                }
        }
    }

    private fun loadFriendsAndLocations() {
        viewModelScope.launch {
            friendRepository.getFriendsFlow()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = "Failed to load friends: ${e.message}")
                }
                .collect { friends ->
                    friendIds = friends.map { it.friendId }
                    
                    if (friendIds.isNotEmpty()) {
                        loadFriendLocations(friends)
                    } else {
                        _uiState.value = _uiState.value.copy(friendLocations = emptyList())
                    }
                }
        }
    }

    private fun loadFriendLocations(friends: List<Friendship>) {
        viewModelScope.launch {
            val friendMap = friends.associateBy { it.friendId }
            
            locationRepository.getFriendsLocationsFlow(friendIds)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = "Failed to load friend locations: ${e.message}")
                }
                .collect { locations ->
                    val locationMap = locations.associateBy { it.userId }
                    
                    val friendLocationInfos = friends.map { friendship ->
                        FriendLocationInfo(
                            friendship = friendship,
                            location = locationMap[friendship.friendId]
                        )
                    }
                    
                    _uiState.value = _uiState.value.copy(friendLocations = friendLocationInfos)
                }
        }
    }

    fun startLocationSharing() {
        if (!_uiState.value.hasLocationPermission) {
            _uiState.value = _uiState.value.copy(error = "Location permission required")
            return
        }

        val serviceIntent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }
        context.startForegroundService(serviceIntent)
        
        viewModelScope.launch {
            userRepository.setLocationSharingEnabled(true)
        }
        
        _uiState.value = _uiState.value.copy(isLocationSharingEnabled = true)
    }

    fun stopLocationSharing() {
        val serviceIntent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }
        context.startService(serviceIntent)
        
        viewModelScope.launch {
            userRepository.setLocationSharingEnabled(false)
            locationRepository.deleteUserLocation()
        }
        
        _uiState.value = _uiState.value.copy(isLocationSharingEnabled = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
