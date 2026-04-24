package com.example.lancamapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lancamapp.CameraDiscovery
import com.example.lancamapp.DiscoveredDevice
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

    // Start scanning automatically when screen opens
    LaunchedEffect(Unit) {
        scope.launch {
            CameraDiscovery(context).findCameras { device ->
                devices = devices + device
            }
            isScanning = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanning Network...") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (isScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onDeviceSelected(device.ip, device.label) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = device.ip, style = MaterialTheme.typography.titleMedium)
                            Text(text = device.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}