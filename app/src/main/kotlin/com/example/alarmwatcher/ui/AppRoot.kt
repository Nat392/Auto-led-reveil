package com.example.alarmwatcher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.alarmwatcher.ui.screens.DashboardScreen
import com.example.alarmwatcher.ui.screens.LogsScreen
import com.example.alarmwatcher.ui.screens.SettingsScreen

private sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Dashboard : AppDestination("dashboard", "Tableau de bord", Icons.Filled.Home)

    data object Settings : AppDestination("settings", "Réglages", Icons.Filled.Settings)

    data object Logs : AppDestination("logs", "Journaux", Icons.AutoMirrored.Filled.List)
}

private val bottomNavDestinations =
    listOf(AppDestination.Dashboard, AppDestination.Settings, AppDestination.Logs)

@Composable
fun AppRoot(
    alarmStatusText: String,
    bleStatusText: String,
    liveStatusText: String,
    onTriggerNightMode: () -> Unit,
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute =
                    navController.currentBackStackEntryAsState().value?.destination?.route

                bottomNavDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Dashboard.route) {
                DashboardScreen(
                    alarmStatusText = alarmStatusText,
                    bleStatusText = bleStatusText,
                    liveStatusText = liveStatusText,
                    onTriggerNightMode = onTriggerNightMode,
                )
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen()
            }
            composable(AppDestination.Logs.route) {
                LogsScreen()
            }
        }
    }
}
