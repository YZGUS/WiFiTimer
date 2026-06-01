package com.cengyi.wifitimer.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cengyi.wifitimer.service.ConnectionState
import com.cengyi.wifitimer.util.TimeUtils
import com.cengyi.wifitimer.util.WifiUtils
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToWhitelist: () -> Unit,
    onNavigateToIgnoreWindows: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTargetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi计时器") },
                actions = {
                    IconButton(onClick = { showTargetDialog = true }) {
                        Icon(Icons.Default.Timer, contentDescription = "设置目标")
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
                    isReached = uiState.isReached,
                    onTargetClick = { showTargetDialog = true }
                )
            }

            // 快捷操作
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onNavigateToWhitelist,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("白名单")
                    }
                    FilledTonalButton(
                        onClick = onNavigateToIgnoreWindows,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("忽略时段")
                    }
                    FilledTonalButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
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

    // 目标时长设置弹窗
    if (showTargetDialog) {
        TargetConfigDialog(
            currentTargetMinutes = uiState.targetMinutes,
            onDismiss = { showTargetDialog = false },
            onConfirm = { hours, minutes ->
                viewModel.updateTarget(hours, minutes)
                showTargetDialog = false
            }
        )
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
    isReached: Boolean,
    onTargetClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    val waveColorLight = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val waveColorDark = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            // Water animation canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val waterLevel = height * (1f - progress.coerceIn(0f, 1f))

                val clipPath = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, width, height, CornerRadius(12.dp.toPx())))
                }

                clipPath(clipPath) {
                    val wavePath1 = Path().apply {
                        moveTo(0f, height)
                        for (x in 0..width.toInt() step 2) {
                            val y = waterLevel + sin(x / width * 2 * Math.PI + waveOffset).toFloat() * 6.dp.toPx()
                            lineTo(x.toFloat(), y)
                        }
                        lineTo(width, height)
                        close()
                    }
                    drawPath(wavePath1, waveColorLight)

                    val wavePath2 = Path().apply {
                        moveTo(0f, height)
                        for (x in 0..width.toInt() step 2) {
                            val y = waterLevel + sin(x / width * 2 * Math.PI + waveOffset + Math.PI).toFloat() * 4.dp.toPx()
                            lineTo(x.toFloat(), y)
                        }
                        lineTo(width, height)
                        close()
                    }
                    drawPath(wavePath2, waveColorDark)
                }
            }

            // Text overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    WifiUtils.formatDuration(effectiveMs),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isReached) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onTargetClick) {
                    Text(
                        "目标 ${WifiUtils.formatDuration(targetMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "修改目标",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

@Composable
private fun TargetConfigDialog(
    currentTargetMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hours by remember { mutableStateOf(currentTargetMinutes / 60) }
    var minutes by remember { mutableStateOf(currentTargetMinutes % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置每日目标时长") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "所有监控WiFi共享同一目标时长，不同楼层/AP的WiFi连接时间将合并计算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hours.toString(),
                        onValueChange = { hours = it.toIntOrNull() ?: 0 },
                        label = { Text("小时") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minutes.toString(),
                        onValueChange = { minutes = it.toIntOrNull() ?: 0 },
                        label = { Text("分钟") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(hours, minutes) },
                enabled = hours > 0 || minutes > 0
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
