package com.friendlocator.app.location

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: LocationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showBackgroundPermissionInfo by remember { mutableStateOf(false) }

    // Permission launchers
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        viewModel.updatePermissions(
            hasForeground = fineLocation || coarseLocation,
            hasBackground = uiState.hasBackgroundPermission
        )
        
        if (fineLocation || coarseLocation) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                showBackgroundPermissionInfo = true
            } else {
                viewModel.startLocationSharing()
            }
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updatePermissions(
            hasForeground = uiState.hasLocationPermission,
            hasBackground = granted
        )
        viewModel.startLocationSharing()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friend Map") },
                actions = {
                    // Location sharing toggle
                    IconButton(
                        onClick = {
                            if (uiState.isLocationSharingEnabled) {
                                viewModel.stopLocationSharing()
                            } else {
                                if (uiState.hasLocationPermission) {
                                    viewModel.startLocationSharing()
                                } else {
                                    showPermissionDialog = true
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (uiState.isLocationSharingEnabled) Icons.Default.LocationOn else Icons.Default.LocationOff,
                            contentDescription = "Toggle Location Sharing",
                            tint = if (uiState.isLocationSharingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Map
            FriendsMap(
                currentLocation = uiState.currentLocation,
                friendLocations = uiState.friendLocations
            )

            // Location Sharing Status Card
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isLocationSharingEnabled) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.isLocationSharingEnabled) Icons.Default.Share else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isLocationSharingEnabled) "Sharing location" else "Location sharing off",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Friend count badge
            if (uiState.friendLocations.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.People, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${uiState.friendLocations.count { it.location != null }} friends nearby",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Permission Dialog
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Location Permission Required") },
                text = { 
                    Text("FriendLocator needs access to your location to share it with your friends. Your location is only visible to people you've added as friends.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            foregroundPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    ) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Background Permission Info Dialog
        if (showBackgroundPermissionInfo) {
            AlertDialog(
                onDismissRequest = { 
                    showBackgroundPermissionInfo = false
                    viewModel.startLocationSharing()
                },
                title = { Text("Background Location") },
                text = { 
                    Text("For the best experience, allow FriendLocator to access your location all the time. This lets your friends see your location even when the app is in the background.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBackgroundPermissionInfo = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                backgroundPermissionLauncher.launch(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                )
                            }
                        }
                    ) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showBackgroundPermissionInfo = false
                            viewModel.startLocationSharing()
                        }
                    ) {
                        Text("Skip")
                    }
                }
            )
        }
    }
}

@Composable
fun FriendsMap(
    currentLocation: com.friendlocator.app.data.models.UserLocation?,
    friendLocations: List<FriendLocationInfo>
) {
    // Default to San Francisco if no location
    val defaultLocation = LatLng(37.7749, -122.4194)
    
    val currentLatLng = currentLocation?.let {
        LatLng(it.latitude, it.longitude)
    } ?: defaultLocation

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLatLng, 13f)
    }

    // Update camera when current location changes
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(LatLng(it.latitude, it.longitude))
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = false,
            mapType = MapType.NORMAL
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            myLocationButtonEnabled = false
        )
    ) {
        // Current user marker
        currentLocation?.let { location ->
            Marker(
                state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                title = "You",
                snippet = "Your current location",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
            )
        }

        // Friend markers
        friendLocations.forEach { friendInfo ->
            friendInfo.location?.let { location ->
                Marker(
                    state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                    title = friendInfo.friendship.friendName.ifEmpty { "Friend" },
                    snippet = friendInfo.friendship.friendEmail,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }
        }
    }
}
