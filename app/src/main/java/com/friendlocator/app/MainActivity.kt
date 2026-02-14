package com.friendlocator.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.friendlocator.app.auth.AuthActivity
import com.friendlocator.app.auth.AuthViewModel
import com.friendlocator.app.friends.FriendsScreen
import com.friendlocator.app.location.MapScreen
import com.friendlocator.app.settings.SettingsScreen
import com.friendlocator.app.ui.theme.FriendLocatorTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var auth: FirebaseAuth

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handle result if needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is authenticated
        if (auth.currentUser == null) {
            navigateToAuth()
            return
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            FriendLocatorTheme {
                MainScreen(
                    navigateTo = intent.getStringExtra("navigate_to"),
                    onSignOut = { navigateToAuth() }
                )
            }
        }
    }

    private fun navigateToAuth() {
        val intent = Intent(this, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

sealed class Screen(val route: String, val icon: @Composable () -> Unit, val label: String) {
    data object Map : Screen(
        "map",
        { Icon(Icons.Default.Map, contentDescription = "Map") },
        "Map"
    )
    data object Friends : Screen(
        "friends",
        { Icon(Icons.Default.People, contentDescription = "Friends") },
        "Friends"
    )
    data object Settings : Screen(
        "settings",
        { Icon(Icons.Default.Settings, contentDescription = "Settings") },
        "Settings"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navigateTo: String? = null,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(Screen.Map, Screen.Friends, Screen.Settings)

    // Handle deep navigation
    LaunchedEffect(navigateTo) {
        navigateTo?.let { destination ->
            when (destination) {
                "friends" -> navController.navigate(Screen.Friends.route)
                "map" -> navController.navigate(Screen.Map.route)
                "settings" -> navController.navigate(Screen.Settings.route)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = screen.icon,
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Map.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Map.route) {
                MapScreen()
            }
            composable(Screen.Friends.route) {
                FriendsScreen(
                    onNavigateToMap = {
                        navController.navigate(Screen.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onSignOut = onSignOut)
            }
        }
    }
}
