package com.example.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class NavigationItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : NavigationItem("home", "Home", Icons.Default.Home)
    object Rules : NavigationItem("rules", "Rules", Icons.AutoMirrored.Filled.List)
    object History : NavigationItem("history", "History", Icons.Default.History)
    object Settings : NavigationItem("settings", "Settings", Icons.Default.Settings)
    object Debug : NavigationItem("debug", "Debug", Icons.Default.BugReport)
    object Verification : NavigationItem("verification", "Verify", Icons.Default.CheckCircle)
    object Permission : NavigationItem("permissions", "Permissions", Icons.Default.Security)
}

@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    rulesViewModel: RulesViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    permissionViewModel: PermissionViewModel,
    serviceViewModel: ServiceViewModel,
    logViewModel: LogViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navigationItems = listOf(
        NavigationItem.Home,
        NavigationItem.Rules,
        NavigationItem.History,
        NavigationItem.Settings,
        NavigationItem.Debug,
        NavigationItem.Verification,
        NavigationItem.Permission
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav_bar")
            ) {
                navigationItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) },
                        modifier = Modifier.testTag("nav_item_${item.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavigationItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavigationItem.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    serviceViewModel = serviceViewModel,
                    onNavigateToTab = { targetRoute ->
                        if (currentRoute != targetRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            composable(NavigationItem.Rules.route) {
                RulesScreen(viewModel = rulesViewModel)
            }
            composable(NavigationItem.History.route) {
                HistoryScreen(viewModel = historyViewModel)
            }
            composable(NavigationItem.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToLogs = { navController.navigate("logs") }
                )
            }
            composable(NavigationItem.Debug.route) {
                DebugDashboardScreen()
            }
            composable(NavigationItem.Verification.route) {
                com.example.verification.ProductionVerificationScreen()
            }
            composable(NavigationItem.Permission.route) {
                PermissionScreen(viewModel = permissionViewModel)
            }
            composable("logs") {
                LogViewerScreen(
                    viewModel = logViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
