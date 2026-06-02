package com.cengyi.wifitimer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cengyi.wifitimer.ui.screen.DashboardScreen
import com.cengyi.wifitimer.ui.screen.HistoryScreen
import com.cengyi.wifitimer.ui.screen.IgnoreWindowsScreen
import com.cengyi.wifitimer.ui.screen.OnboardingScreen
import com.cengyi.wifitimer.ui.screen.WhitelistScreen

@Composable
fun NavGraph(navController: NavHostController, startDestination: String = Screen.Dashboard.route) {
    NavHost(navController, startDestination = startDestination) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToWhitelist = { navController.navigate(Screen.Whitelist.route) },
                onNavigateToIgnoreWindows = { navController.navigate(Screen.IgnoreWindows.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) }
            )
        }
        composable(Screen.Whitelist.route) {
            WhitelistScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.IgnoreWindows.route) {
            IgnoreWindowsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.History.route) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onAddWifi = { navController.navigate(Screen.Whitelist.route) },
                onComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
