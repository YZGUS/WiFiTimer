package com.cengyi.wifitimer.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.material3.*
import androidx.glance.text.*
import com.cengyi.wifitimer.data.repository.SessionRepository
import com.cengyi.wifitimer.data.repository.TargetConfigRepository
import com.cengyi.wifitimer.data.repository.IgnoreWindowRepository
import com.cengyi.wifitimer.service.ConnectionState
import com.cengyi.wifitimer.service.WiFiMonitorService
import com.cengyi.wifitimer.util.IgnoreWindowCalculator
import com.cengyi.wifitimer.util.TimeUtils
import com.cengyi.wifitimer.util.WifiUtils
import dagger.hilt.android.EntryPointAccessors

class TimerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadWidgetState(context)
        provideContent {
            WidgetContent(state)
        }
    }

    data class WidgetState(
        val ssid: String?,
        val connected: Boolean,
        val serviceRunning: Boolean,
        val effectiveMs: Long,
        val targetMs: Long,
        val progress: Float
    ) {
        val isReached: Boolean get() = effectiveMs >= targetMs && targetMs > 0
    }

    companion object {
        suspend fun triggerUpdate(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val widget = TimerWidget()
            manager.getGlanceIds(TimerWidget::class.java).forEach { id ->
                widget.update(context, id)
            }
        }
    }
}

private suspend fun loadWidgetState(context: Context): TimerWidget.WidgetState {
    val connectionState = WiFiMonitorService.connectionState.value
    val serviceRunning = WiFiMonitorService.serviceRunning.value

    val ssid = when (connectionState) {
        is ConnectionState.Connected -> connectionState.ssid
        else -> null
    }
    val connected = connectionState is ConnectionState.Connected

    val entryPoint = EntryPointAccessors.fromApplication(
        context, WidgetRepositoryEntryPoint::class.java
    )
    val sessionRepo = entryPoint.sessionRepository()
    val targetConfigRepo = entryPoint.targetConfigRepository()
    val ignoreWindowRepo = entryPoint.ignoreWindowRepository()

    val todayStr = TimeUtils.todayStr()
    val totalMs = sessionRepo.getEffectiveTotalForDate(todayStr)
    val targetMinutes = targetConfigRepo.getTargetMinutes()
    val targetMs = targetMinutes * 60_000L
    val windows = ignoreWindowRepo.getEnabledList()
    val now = System.currentTimeMillis()

    val effectiveMs = if (connected && connectionState is ConnectionState.Connected) {
        val activeEffective = IgnoreWindowCalculator.computeEffectiveDuration(
            connectionState.startTime, now, windows
        )
        totalMs + activeEffective
    } else {
        totalMs
    }

    val progress = if (targetMs > 0) (effectiveMs.toFloat() / targetMs).coerceIn(0f, 1f) else 0f

    return TimerWidget.WidgetState(
        ssid = ssid,
        connected = connected,
        serviceRunning = serviceRunning,
        effectiveMs = effectiveMs,
        targetMs = targetMs,
        progress = progress
    )
}

@Composable
private fun WidgetContent(state: TimerWidget.WidgetState) {
    val durationText = WifiUtils.formatDurationCompact(state.effectiveMs)
    val targetText = WifiUtils.formatDurationCompact(state.targetMs)

    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.connected && state.ssid != null) {
                    Text(
                        text = state.ssid,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.primary
                        ),
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = if (state.serviceRunning) "未连接WiFi" else "服务未启动",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = durationText,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(4.dp)
                )

                Spacer(modifier = GlanceModifier.height(3.dp))

                Text(
                    text = if (state.isReached) "已达标" else "目标 $targetText",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimerWidget()
}