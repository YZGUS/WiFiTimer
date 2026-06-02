package com.cengyi.wifitimer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.rememberNavController
import com.cengyi.wifitimer.data.repository.UserPrefsRepository
import com.cengyi.wifitimer.service.WiFiMonitorService
import com.cengyi.wifitimer.ui.navigation.NavGraph
import com.cengyi.wifitimer.ui.navigation.Screen
import com.cengyi.wifitimer.ui.theme.WiFiTimerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : androidx.activity.ComponentActivity() {

    @Inject lateinit var userPrefsRepo: UserPrefsRepository

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            WiFiTimerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val onboardingCompleted by userPrefsRepo.onboardingCompleted
                        .collectAsState(initial = null)

                    when (onboardingCompleted) {
                        null -> {
                            // Loading
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        true -> {
                            val navController = rememberNavController()
                            NavGraph(navController)
                            LaunchedEffect(Unit) {
                                WiFiMonitorService.start(this@MainActivity)
                            }
                        }
                        false -> {
                            val navController = rememberNavController()
                            NavGraph(navController, startDestination = Screen.Onboarding.route)
                            LaunchedEffect(Unit) {
                                WiFiMonitorService.start(this@MainActivity)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        WiFiMonitorService.start(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}
