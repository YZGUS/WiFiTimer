package com.cengyi.wifitimer.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cengyi.wifitimer.service.ConnectionState
import com.cengyi.wifitimer.service.WiFiMonitorService
import com.cengyi.wifitimer.util.TimeUtils
import com.cengyi.wifitimer.util.WifiUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToWhitelist: () -> Unit,
    onNavigateToIgnoreWindows: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi计时器") },
                actions = {
                    IconButton(onClick = {
                        if (uiState.isServiceRunning) {
                            WiFiMonitorService.stop(context)
                        } else {
                            WiFiMonitorService.start(context)
                        }
                    }) {
                        Icon(
                            if (uiState.isServiceRunning) Icons.Default.Stop
                            else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isServiceRunning) "停止监控" else "开始监控"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 连接状态卡片
            item {
                ConnectionStatusCard(uiState.connectionState)
            }

            // 进度卡片
            item {
                ProgressCard(
                    effectiveMs = uiState.todayEffectiveMs,
                    targetMs = uiState.targetMs,
                    progress = uiState.progress,
                    isReached = uiState.isReached
                )
            }

            // 快捷操作
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToWhitelist,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("白名单")
                    }
                    OutlinedButton(
                        onClick = onNavigateToIgnoreWindows,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("忽略时段")
                    }
                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("历史")
                    }
                }
            }

            // 今日 Session 列表
            item {
                Text(
                    "今日连接记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.sessions.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(uiState.sessions) { session ->
                    SessionItem(session)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                is ConnectionState.Disconnected -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (state) {
                    is ConnectionState.Connected -> Icons.Default.Wifi
                    is ConnectionState.Disconnected -> Icons.Default.WifiOff
                },
                contentDescription = null,
                tint = when (state) {
                    is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                    is ConnectionState.Disconnected -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    when (state) {
                        is ConnectionState.Connected -> "已连接：${state.ssid}"
                        is ConnectionState.Disconnected -> "未连接目标WiFi"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state is ConnectionState.Connected) {
                    Text(
                        "自 ${TimeUtils.toTimeStr(state.startTime)} 起",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    effectiveMs: Long,
    targetMs: Long,
    progress: Float,
    isReached: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                WifiUtils.formatDuration(effectiveMs),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = if (isReached) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "目标 ${WifiUtils.formatDuration(targetMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (isReached) {
                Text(
                    "今日已达标",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    "还差 ${WifiUtils.formatDuration(targetMs - effectiveMs)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionItem(session: com.cengyi.wifitimer.data.local.WiFiSession) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(session.ssid, fontWeight = FontWeight.Medium)
                Text(
                    "${TimeUtils.toTimeStr(session.startTime)} - ${TimeUtils.toTimeStr(session.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                WifiUtils.formatDuration(session.effectiveDurationMs),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
