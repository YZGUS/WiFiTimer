package com.cengyi.wifitimer.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onAddWifi: () -> Unit,
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val targetHours by viewModel.targetHours.collectAsState()
    val targetMinutes by viewModel.targetMinutes.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })

    var hoursExpanded by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        locationGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        notificationGranted = perms[Manifest.permission.POST_NOTIFICATIONS] == true
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            // Skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    viewModel.completeOnboarding()
                    onComplete()
                }) {
                    Text("跳过")
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingWifiPage(onAddWifi = onAddWifi)
                    1 -> OnboardingTargetPage(
                        hours = targetHours,
                        minutes = targetMinutes,
                        hoursExpanded = hoursExpanded,
                        onHoursExpandedChange = { hoursExpanded = it },
                        onHoursChange = { viewModel.setTargetHours(it) },
                        onMinutesChange = { viewModel.setTargetMinutes(it) }
                    )
                    2 -> OnboardingPermissionsPage(
                        locationGranted = locationGranted,
                        notificationGranted = notificationGranted,
                        onRequestPermissions = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            )
                        },
                        onComplete = {
                            viewModel.completeOnboarding()
                            onComplete()
                        }
                    )
                }
            }

            // Page indicator + action button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingWifiPage(onAddWifi: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "添加监控WiFi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "将常驻的WiFi添加到白名单\n进入范围自动计时",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        FilledTonalButton(onClick = onAddWifi) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("添加WiFi")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingTargetPage(
    hours: Int,
    minutes: Int,
    hoursExpanded: Boolean,
    onHoursExpandedChange: (Boolean) -> Unit,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Timer,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "设置每日目标",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "设定工作时长目标\n达标时收到提醒",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        // Hours dropdown
        Text("小时", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = hoursExpanded,
            onExpandedChange = onHoursExpandedChange
        ) {
            OutlinedTextField(
                value = "${hours}小时",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(hoursExpanded) },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .menuAnchor(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = hoursExpanded,
                onDismissRequest = { onHoursExpandedChange(false) }
            ) {
                (0..12).forEach { h ->
                    DropdownMenuItem(
                        text = { Text("${h}小时") },
                        onClick = {
                            onHoursChange(h)
                            onHoursExpandedChange(false)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Minutes chips
        Text("分钟", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 15, 30, 45).forEach { m ->
                FilterChip(
                    selected = minutes == m,
                    onClick = { onMinutesChange(m) },
                    label = { Text("${m}分") }
                )
            }
        }
    }
}

@Composable
private fun OnboardingPermissionsPage(
    locationGranted: Boolean,
    notificationGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "开启必要权限",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "位置权限用于识别WiFi\n通知权限用于断开提醒",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        // Permission status
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (locationGranted) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (locationGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text("位置权限")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (notificationGranted) Icons.Default.CheckCircle else Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (notificationGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text("通知权限")
            }
        }

        Spacer(Modifier.height(32.dp))

        if (!locationGranted || !notificationGranted) {
            FilledTonalButton(onClick = onRequestPermissions) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("授权")
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onComplete) {
            Text("完成设置")
        }
    }
}
