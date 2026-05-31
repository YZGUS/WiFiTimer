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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cengyi.wifitimer.data.local.IgnoreWindow
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoreWindowsScreen(
    onBack: () -> Unit,
    viewModel: IgnoreWindowsViewModel = hiltViewModel()
) {
    val windows by viewModel.windows.collectAsState()
    val editState by viewModel.editState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("忽略时段") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startNew() }) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        }
    ) { padding ->
        if (windows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("暂无忽略时段", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("添加忽略时段以排除非工作时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(windows, key = { it.id }) { window ->
                    IgnoreWindowItem(
                        window = window,
                        onToggle = { viewModel.toggleEnabled(window) },
                        onEdit = { viewModel.startEdit(window) },
                        onDelete = { viewModel.deleteWindow(window.id) }
                    )
                }
            }
        }
    }

    // 编辑/添加弹窗
    editState?.let { state ->
        EditIgnoreWindowDialog(
            state = state,
            onDismiss = { viewModel.clearEdit() },
            onConfirm = { label, sh, sm, eh, em, days ->
                viewModel.saveFromDialog(label, sh, sm, eh, em, days)
            }
        )
    }
}

@Composable
private fun IgnoreWindowItem(
    window: IgnoreWindow,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = window.enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(window.label, fontWeight = FontWeight.Medium)
                Text(
                    "%02d:%02d - %02d:%02d".format(
                        window.startHour, window.startMinute,
                        window.endHour, window.endMinute
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (window.repeatDays.isNotEmpty()) {
                    Text(
                        window.repeatDays.joinToString(" ") {
                            it.name.take(3)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "仅今日",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditIgnoreWindowDialog(
    state: IgnoreWindowEditState,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Int, Int, Set<DayOfWeek>) -> Unit
) {
    var label by remember { mutableStateOf(state.label) }
    var startHour by remember { mutableIntStateOf(state.startHour) }
    var startMinute by remember { mutableIntStateOf(state.startMinute) }
    var endHour by remember { mutableIntStateOf(state.endHour) }
    var endMinute by remember { mutableIntStateOf(state.endMinute) }
    var selectedDays by remember { mutableStateOf(state.repeatDays) }
    val showStartTimePicker = remember { mutableStateOf(false) }
    val showEndTimePicker = remember { mutableStateOf(false) }

    if (showStartTimePicker.value) {
        TimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { h, m ->
                startHour = h
                startMinute = m
                showStartTimePicker.value = false
            },
            onDismiss = { showStartTimePicker.value = false }
        )
    }

    if (showEndTimePicker.value) {
        TimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { h, m ->
                endHour = h
                endMinute = m
                showEndTimePicker.value = false
            },
            onDismiss = { showEndTimePicker.value = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.isNew) "添加忽略时段" else "编辑忽略时段") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("标签（如：午餐）") },
                    singleLine = true
                )

                // 开始时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("开始时间")
                    TextButton(onClick = { showStartTimePicker.value = true }) {
                        Text("%02d:%02d".format(startHour, startMinute))
                    }
                }

                // 结束时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("结束时间")
                    TextButton(onClick = { showEndTimePicker.value = true }) {
                        Text("%02d:%02d".format(endHour, endMinute))
                    }
                }

                // 重复日选择
                Text("重复日", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val dayLabels = mapOf(
                        DayOfWeek.MONDAY to "一",
                        DayOfWeek.TUESDAY to "二",
                        DayOfWeek.WEDNESDAY to "三",
                        DayOfWeek.THURSDAY to "四",
                        DayOfWeek.FRIDAY to "五",
                        DayOfWeek.SATURDAY to "六",
                        DayOfWeek.SUNDAY to "日"
                    )
                    dayLabels.forEach { (day, label) ->
                        val selected = day in selectedDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedDays = if (selected) {
                                    selectedDays - day
                                } else {
                                    selectedDays + day
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(label, startHour, startMinute, endHour, endMinute, selectedDays)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}
