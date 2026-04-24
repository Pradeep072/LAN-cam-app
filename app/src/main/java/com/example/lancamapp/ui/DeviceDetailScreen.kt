package com.example.lancamapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lancamapp.database.AppDatabase
import com.example.lancamapp.database.CameraEntity
import com.example.lancamapp.utils.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    camera: CameraEntity,
    onBack: () -> Unit,
    onPlaySelected: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    // UI State
    var channelCount by remember { mutableStateOf(if (camera.channelCount > 0) camera.channelCount else 1) }
    val selectedChannels = remember { mutableStateListOf<Int>() }
    var isProbing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) } // <--- New State

    // --- AUTO PROBE ON LAUNCH ---
    LaunchedEffect(Unit) {
        if (camera.channelCount == 0) {
            isProbing = true
            scope.launch(Dispatchers.IO) {
                val detectedCount = DeviceUtils.probeChannelCount(camera.type)
                channelCount = detectedCount
                val updatedCam = camera.copy(channelCount = detectedCount)
                db.cameraDao().insertCamera(updatedCam)
                isProbing = false
            }
        }
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Camera?") },
            text = { Text("Are you sure you want to remove '${camera.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            // 1. Delete from DB
                            db.cameraDao().deleteCamera(camera)
                            // 2. Close Dialog
                            showDeleteDialog = false
                            // 3. Go back to Dashboard
                            onBack()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(camera.name)
                        Text(if(isProbing) "Detecting channels..." else "${camera.type}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    // --- DELETE BUTTON ---
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete Device", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            if (selectedChannels.isNotEmpty()) {
                Button(
                    onClick = {
                        val urls = selectedChannels.sorted().map { ch ->
                            DeviceUtils.generateUrlForChannel(camera, ch)
                        }
                        onPlaySelected(urls)
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Watch Selected (${selectedChannels.size})")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            if (isProbing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text("Available Channels", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))

            // Grid of Channels
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 70.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(channelCount) { index ->
                    val num = index + 1
                    val isSelected = selectedChannels.contains(num)
                    ChannelBox(num, isSelected) {
                        if (isSelected) selectedChannels.remove(num) else selectedChannels.add(num)
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelBox(num: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CH $num", color = if (isSelected) Color.White else Color.Black, fontWeight = FontWeight.Bold)
            if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}