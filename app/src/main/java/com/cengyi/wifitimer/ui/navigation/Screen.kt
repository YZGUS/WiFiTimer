package com.cengyi.wifitimer.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Whitelist : Screen("whitelist")
    data object IgnoreWindows : Screen("ignore_windows")
    data object History : Screen("history")
}
