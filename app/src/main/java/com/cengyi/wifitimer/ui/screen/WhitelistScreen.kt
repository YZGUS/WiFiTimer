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
import com.cengyi.wifitimer.data.local.WiFiWhitelistEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(
    onBack: () -> Unit,
    viewModel: WhitelistViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val editEntry by viewModel.editEntry.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi白名单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("暂无白名单条目", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("点击 + 添加要监控的WiFi", style = MaterialTheme.typography.bodySmall,
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
                items(entries, key = { it.id }) { entry ->
                    WhitelistItem(
                        entry = entry,
                        onToggle = { viewModel.toggleEnabled(entry) },
                        onEdit = { viewModel.startEdit(entry) },
                        onDelete = { viewModel.deleteEntry(entry.id) }
                    )
                }
            }
        }
    }

    // 添加弹窗
    if (showAddDialog) {
        AddWhitelistDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { ssid, bssid, alias, targetMinutes ->
                viewModel.addEntry(ssid, bssid, alias, targetMinutes)
                showAddDialog = false
            }
        )
    }

    // 编辑弹窗
    editEntry?.let { entry ->
        EditWhitelistDialog(
            entry = entry,
            onDismiss = { viewModel.clearEdit() },
            onConfirm = { updated ->
                viewModel.updateEntry(updated)
                viewModel.clearEdit()
            }
        )
    }
}

@Composable
private fun WhitelistItem(
    entry: WiFiWhitelistEntry,
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
                checked = entry.enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.alias.ifBlank { entry.ssid },
                    fontWeight = FontWeight.Medium
                )
                Text(
                    entry.ssid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.bssid != null) {
                    Text(
                        "BSSID: ${entry.bssid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "目标：${entry.targetMinutes / 60}h ${entry.targetMinutes % 60}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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

@Composable
private fun AddWhitelistDialog(
    onDismiss: () -> Unit,
    onConfirm: (ssid: String, bssid: String?, alias: String, targetMinutes: Int) -> Unit
) {
    var ssid by remember { mutableStateOf("") }
    var bssid by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var targetHours by remember { mutableStateOf(9) }
    var targetMinutesPart by remember { mutableStateOf(30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加WiFi白名单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("WiFi名称 (SSID)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = bssid,
                    onValueChange = { bssid = it },
                    label = { Text("BSSID（可选）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("备注别名") },
                    singleLine = true
                )
                Text("每日目标时长", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetHours.toString(),
                        onValueChange = { targetHours = it.toIntOrNull() ?: 0 },
                        label = { Text("小时") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetMinutesPart.toString(),
                        onValueChange = { targetMinutesPart = it.toIntOrNull() ?: 0 },
                        label = { Text("分钟") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (ssid.isNotBlank()) {
                        onConfirm(
                            ssid.trim(),
                            bssid.ifBlank { null },
                            alias.trim(),
                            targetHours * 60 + targetMinutesPart
                        )
                    }
                },
                enabled = ssid.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EditWhitelistDialog(
    entry: WiFiWhitelistEntry,
    onDismiss: () -> Unit,
    onConfirm: (WiFiWhitelistEntry) -> Unit
) {
    var alias by remember { mutableStateOf(entry.alias) }
    var targetHours by remember { mutableStateOf(entry.targetMinutes / 60) }
    var targetMinutesPart by remember { mutableStateOf(entry.targetMinutes % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑白名单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SSID: ${entry.ssid}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.bssid != null) {
                    Text("BSSID: ${entry.bssid}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("备注别名") },
                    singleLine = true
                )
                Text("每日目标时长", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetHours.toString(),
                        onValueChange = { targetHours = it.toIntOrNull() ?: 0 },
                        label = { Text("小时") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetMinutesPart.toString(),
                        onValueChange = { targetMinutesPart = it.toIntOrNull() ?: 0 },
                        label = { Text("分钟") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        entry.copy(
                            alias = alias.trim(),
                            targetMinutes = targetHours * 60 + targetMinutesPart
                        )
                    )
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
