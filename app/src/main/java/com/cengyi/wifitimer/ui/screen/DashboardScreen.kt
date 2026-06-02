package com.cengyi.wifitimer.ui.screen

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cengyi.wifitimer.service.ConnectionState
import com.cengyi.wifitimer.util.TimeUtils
import com.cengyi.wifitimer.util.WifiUtils
import kotlin.math.sin

@Composable
private fun isReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return try {
        Settings.System.getFloat(
            context.contentResolver,
            Settings.System.TRANSITION_ANIMATION_SCALE
        ) == 0f
    } catch (_: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onNavigateToWhitelist: () -> Unit,
    onNavigateToIgnoreWindows: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTargetDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi计时器") },
                actions = {
                    ServiceStatusDot(isRunning = uiState.isServiceRunning)
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
                ConnectionStatusCard(
                    state = uiState.connectionState,
                    whitelistCount = uiState.whitelistCount
                )
            }

            // 进度卡片
            item {
                ProgressCard(
                    effectiveMs = uiState.todayEffectiveMs,
                    targetMs = uiState.targetMs,
                    progress = uiState.progress,
                    isReached = uiState.isReached,
                    isFrozen = uiState.isFrozen,
                    onTargetClick = { showTargetDialog = true }
                )
            }

            // 快捷操作
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onNavigateToWhitelist,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("白名单", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onNavigateToIgnoreWindows,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("忽略时段", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("历史", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                intent.action = "android.appwidget.ADD_APPWIDGET"
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("小插件", maxLines = 1)
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
                    EmptySessionCard(
                        reason = uiState.emptyReason ?: EmptyReason.NOT_CONNECTED,
                        onAddWifi = onNavigateToWhitelist
                    )
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
private fun ServiceStatusDot(isRunning: Boolean) {
    val reduceMotion = isReduceMotionEnabled()
    val infiniteTransition = rememberInfiniteTransition(label = "serviceDot")
    val pulseAlpha by if (reduceMotion) {
        mutableStateOf(1f)
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotPulse"
        )
    }

    val dotColor = if (isRunning) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .size(24.dp)
            .padding(7.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(
                color = dotColor.copy(alpha = pulseAlpha)
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: ConnectionState, whitelistCount: Int) {
    val reduceMotion = isReduceMotionEnabled()
    val animSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween(400)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                is ConnectionState.Disconnected -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(animSpec) togetherWith fadeOut(animSpec) using SizeTransform(clip = false)
            },
            label = "connectionState"
        ) { currentState ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (currentState) {
                        is ConnectionState.Connected -> Icons.Default.Wifi
                        is ConnectionState.Disconnected -> Icons.Default.WifiOff
                    },
                    contentDescription = null,
                    tint = when (currentState) {
                        is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                        is ConnectionState.Disconnected -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        when (currentState) {
                            is ConnectionState.Connected -> "已连接：${currentState.ssid}"
                            is ConnectionState.Disconnected -> "未连接目标WiFi"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    when (currentState) {
                        is ConnectionState.Connected -> {
                            Text(
                                "自 ${TimeUtils.toTimeStr(currentState.startTime)} 起",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        is ConnectionState.Disconnected -> {
                            Text(
                                if (whitelistCount == 0) "请先添加WiFi到白名单"
                                else "请确保WiFi已开启且在白名单范围内",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
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
    isFrozen: Boolean,
    onTargetClick: () -> Unit
) {
    val reduceMotion = isReduceMotionEnabled()
    val colorScheme = MaterialTheme.colorScheme

    val animatedProgress = remember { androidx.compose.animation.core.Animatable(progress) }
    LaunchedEffect(progress) {
        if (reduceMotion) {
            animatedProgress.snapTo(progress)
        } else {
            animatedProgress.animateTo(progress, tween(800))
        }
    }

    val highWater = progress > 0.5f
    val mainTextColor by animateColorAsState(
        targetValue = when {
            isFrozen -> colorScheme.onSurface.copy(alpha = 0.6f)
            isReached -> colorScheme.onPrimary
            highWater -> colorScheme.onPrimary
            else -> colorScheme.onSurface
        },
        animationSpec = tween(500),
        label = "mainTextColor"
    )
    val subTextColor by animateColorAsState(
        targetValue = when {
            isFrozen -> colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            highWater -> colorScheme.onPrimary.copy(alpha = 0.7f)
            else -> colorScheme.onSurfaceVariant
        },
        animationSpec = tween(500),
        label = "subTextColor"
    )
    val unitTextColor by animateColorAsState(
        targetValue = when {
            isFrozen -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            highWater -> colorScheme.onPrimary.copy(alpha = 0.5f)
            else -> colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(500),
        label = "unitTextColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val animatedWaveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )
    val waveOffset = if (isFrozen || reduceMotion) 0f else animatedWaveOffset

    val waveColorLight = if (isFrozen) colorScheme.primary.copy(alpha = 0.15f)
    else colorScheme.primary.copy(alpha = 0.25f)
    val waveColorDark = if (isFrozen) colorScheme.primary.copy(alpha = 0.25f)
    else colorScheme.primary.copy(alpha = 0.5f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val waterLevel = height * (1f - animatedProgress.value.coerceIn(0f, 1f))

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StyledTimeDisplay(
                    ms = effectiveMs,
                    mainTextColor = mainTextColor,
                    unitTextColor = unitTextColor,
                    reduceMotion = reduceMotion
                )
                Spacer(Modifier.height(4.dp))
                TargetInfoRow(
                    targetMs = targetMs,
                    subTextColor = subTextColor,
                    onClick = onTargetClick,
                    reduceMotion = reduceMotion
                )
                Spacer(Modifier.height(8.dp))
                if (isFrozen) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = subTextColor
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "忽略时段 · 已冻结",
                            color = subTextColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!isReached) {
                        Text(
                            "还差 ${WifiUtils.formatDuration(targetMs - effectiveMs)}",
                            color = subTextColor
                        )
                    }
                } else if (isReached) {
                    Text(
                        "今日已达标",
                        color = mainTextColor,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        "还差 ${WifiUtils.formatDuration(targetMs - effectiveMs)}",
                        color = subTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun StyledTimeDisplay(
    ms: Long,
    mainTextColor: androidx.compose.ui.graphics.Color,
    unitTextColor: androidx.compose.ui.graphics.Color,
    reduceMotion: Boolean
) {
    val parts = WifiUtils.decomposeDuration(ms)

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        if (parts.showHours) {
            AnimatedTimeSegment(
                value = parts.hours,
                unit = "时",
                padded = false,
                numberColor = mainTextColor,
                unitColor = unitTextColor,
                reduceMotion = reduceMotion,
                label = "hours"
            )
            Spacer(Modifier.width(8.dp))
        }
        if (parts.showMinutes) {
            AnimatedTimeSegment(
                value = parts.minutes,
                unit = "分",
                padded = parts.showHours,
                numberColor = mainTextColor,
                unitColor = unitTextColor,
                reduceMotion = reduceMotion,
                label = "minutes"
            )
            Spacer(Modifier.width(8.dp))
        }
        AnimatedTimeSegment(
            value = parts.seconds,
            unit = "秒",
            padded = parts.showMinutes,
            numberColor = mainTextColor,
            unitColor = unitTextColor,
            reduceMotion = reduceMotion,
            label = "seconds"
        )
    }
}

@Composable
private fun AnimatedTimeSegment(
    value: Int,
    unit: String,
    padded: Boolean,
    numberColor: androidx.compose.ui.graphics.Color,
    unitColor: androidx.compose.ui.graphics.Color,
    reduceMotion: Boolean,
    label: String
) {
    val animSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween(200)

    AnimatedContent(
        targetState = value,
        transitionSpec = {
            fadeIn(animSpec) togetherWith fadeOut(animSpec) using SizeTransform(clip = false)
        },
        label = label
    ) { targetValue ->
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (padded) String.format("%02d", targetValue) else targetValue.toString(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = numberColor
            )
            Text(
                text = unit,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = unitColor,
                modifier = Modifier.padding(bottom = 5.dp, start = 2.dp)
            )
        }
    }
}

@Composable
private fun TargetInfoRow(
    targetMs: Long,
    subTextColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    reduceMotion: Boolean
) {
    val animSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween(300)

    TextButton(onClick = onClick) {
        AnimatedContent(
            targetState = targetMs,
            transitionSpec = {
                fadeIn(animSpec) togetherWith fadeOut(animSpec) using SizeTransform(clip = false)
            },
            label = "targetMs"
        ) { animatedTargetMs ->
            Text(
                "目标 ${WifiUtils.formatDuration(animatedTargetMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = subTextColor
            )
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = "修改目标",
            modifier = Modifier.size(14.dp),
            tint = subTextColor
        )
    }
}

@Composable
private fun EmptySessionCard(reason: EmptyReason, onAddWifi: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (reason) {
                EmptyReason.NO_WHITELIST -> {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("还没有监控的WiFi", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "添加常驻WiFi到白名单后自动计时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onAddWifi) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加WiFi")
                    }
                }
                EmptyReason.NOT_CONNECTED -> {
                    Icon(
                        Icons.Default.WifiFind,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("当前未连接目标WiFi", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "连接白名单中的WiFi后自动开始计时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EmptyReason.CONNECTED_NO_TIME -> {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("刚开始记录", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "连接时间不足30秒不计入有效时长",
                        style = MaterialTheme.typography.bodySmall,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(session.ssid, fontWeight = FontWeight.Medium)
                Text(
                    "${TimeUtils.toTimeStr(session.startTime)} - ${TimeUtils.toTimeStr(session.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                WifiUtils.formatDurationCompact(session.effectiveDurationMs),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TargetConfigDialog(
    currentTargetMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hours by remember { mutableStateOf(currentTargetMinutes / 60) }
    var minutes by remember { mutableStateOf(currentTargetMinutes % 60) }
    var hoursExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置每日目标时长") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "所有监控WiFi共享同一目标时长，不同楼层/AP的WiFi连接时间将合并计算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Hours dropdown
                Text("小时", style = MaterialTheme.typography.labelMedium)
                ExposedDropdownMenuBox(
                    expanded = hoursExpanded,
                    onExpandedChange = { hoursExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "${hours}小时",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(hoursExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = hoursExpanded,
                        onDismissRequest = { hoursExpanded = false }
                    ) {
                        (0..12).forEach { h ->
                            DropdownMenuItem(
                                text = { Text("${h}小时") },
                                onClick = {
                                    hours = h
                                    hoursExpanded = false
                                }
                            )
                        }
                    }
                }

                // Minutes chips
                Text("分钟", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 15, 30, 45).forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { minutes = m },
                            label = { Text("${m}分") }
                        )
                    }
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
