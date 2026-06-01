package com.cengyi.wifitimer.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cengyi.wifitimer.data.local.WiFiWhitelistEntry
import com.cengyi.wifitimer.util.WifiScanner

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

    if (showAddDialog) {
        AddWhitelistDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { ssid, alias ->
                viewModel.addEntry(ssid, null, alias)
                showAddDialog = false
            }
        )
    }

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
private fun AddWhitelistDialog(
    onDismiss: () -> Unit,
    onConfirm: (ssid: String, alias: String) -> Unit
) {
    val context = LocalContext.current
    var ssid by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Scan WiFi after permission is granted
    val wifiList = remember(hasLocationPermission) {
        if (hasLocationPermission) {
            WifiScanner.startScan(context)
            WifiScanner.getAvailableNetworks(context)
        } else {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加WiFi白名单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { newExpanded ->
                        if (newExpanded && !hasLocationPermission) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            return@ExposedDropdownMenuBox
                        }
                        expanded = newExpanded
                    }
                ) {
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = {
                            ssid = it
                            expanded = false
                        },
                        label = { Text("WiFi名称") },
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier.menuAnchor()
                    )

                    if (wifiList.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            wifiList.forEach { network ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Wifi,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (network.level > -50) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(network.ssid)
                                        }
                                    },
                                    onClick = {
                                        ssid = network.ssid
                                        if (alias.isBlank()) alias = network.ssid
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Manual input hint when no scan results
                if (wifiList.isEmpty() && !hasLocationPermission) {
                    Text(
                        "点击输入框右侧图标授权位置权限，即可扫描附近WiFi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (wifiList.isEmpty() && hasLocationPermission) {
                    Text(
                        "未扫描到WiFi，请手动输入WiFi名称",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("备注别名（可选）") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (ssid.isNotBlank()) {
                        onConfirm(ssid.trim(), alias.trim())
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑白名单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WiFi: ${entry.ssid}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("备注别名") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(entry.copy(alias = alias.trim()))
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
