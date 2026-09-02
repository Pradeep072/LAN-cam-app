package com.example.lancamapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lancamapp.CameraDiscovery
import com.example.lancamapp.DiscoveredDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onDeviceSelected: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf(listOf<DiscoveredDevice>()) }
    var isScanning by remember { mutableStateOf(true) }
    var scanModeTitle by remember { mutableStateOf("Scanning LAN & Subnet...") }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var showManualIpDialog by remember { mutableStateOf(false) }

    fun startScan(deepScan: Boolean = false) {
        scanJob?.cancel()
        isScanning = true
        scanModeTitle = if (deepScan) "Deep Scanning (All Ports & Extenders)..." else "Scanning Multi-Port & ONVIF..."
        scanJob = scope.launch {
            CameraDiscovery(context).findCameras(deepScan = deepScan) { device ->
                if (devices.none { it.ip == device.ip }) {
                    devices = devices + device
                }
            }
            isScanning = false
            scanModeTitle = "Scan Complete"
        }
    }

    // Start scanning automatically on launch
    LaunchedEffect(Unit) {
        startScan(deepScan = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover LAN Cameras") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showManualIpDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add IP")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Header Info & Scan Control Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = scanModeTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isScanning) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${devices.size} device(s) found",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Rescan Button
                            Button(
                                onClick = { startScan(deepScan = false) },
                                enabled = !isScanning,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rescan")
                            }

                            // Deep Scan Button (Multi-Port & Subnets for Extenders)
                            OutlinedButton(
                                onClick = { startScan(deepScan = true) },
                                enabled = !isScanning,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Deep Scan")
                            }
                        }
                    }

                    if (isScanning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFFFFD700))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (devices.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No cameras found automatically", color = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { startScan(deepScan = true) }) {
                                Text("Run Deep Scan")
                            }
                            OutlinedButton(onClick = { showManualIpDialog = true }) {
                                Text("Enter IP Manually")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(devices) { device ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .border(
                                    width = if (isFocused) 3.dp else 0.dp,
                                    color = if (isFocused) Color(0xFFFFD700) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusable(interactionSource = interactionSource)
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    onDeviceSelected(device.ip, device.label)
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = device.ip,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = device.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (device.label.contains("CP Plus")) Color(0xFF00C853) else MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Text(
                                    text = "SELECT →",
                                    color = if (isFocused) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Manual IP Dialog
        if (showManualIpDialog) {
            var manualIp by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showManualIpDialog = false },
                title = { Text("Add Camera by IP") },
                text = {
                    Column {
                        Text(
                            "Enter the IP address of your camera or Wi-Fi extender device (e.g. 192.168.1.50):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = { Text("Camera IP Address") },
                            placeholder = { Text("192.168.1.xxx") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (manualIp.isNotBlank()) {
                                showManualIpDialog = false
                                onDeviceSelected(manualIp.trim(), "Manual Camera")
                            }
                        }
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualIpDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}