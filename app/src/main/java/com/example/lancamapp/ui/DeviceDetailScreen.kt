package com.example.lancamapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
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
    var currentCamera by remember { mutableStateOf(camera) }
    var channelCount by remember { mutableIntStateOf(if (camera.channelCount > 0) camera.channelCount else 1) }
    val selectedChannels = remember { mutableStateListOf<Int>() }
    var isProbing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Stream quality state
    var selectedStreamType by remember { mutableStateOf(currentCamera.streamType) }

    // --- AUTO PROBE ON LAUNCH ---
    LaunchedEffect(Unit) {
        if (currentCamera.channelCount == 0) {
            isProbing = true
            scope.launch(Dispatchers.IO) {
                val detectedCount = DeviceUtils.probeChannelCount(currentCamera.type)
                channelCount = detectedCount
                val updatedCam = currentCamera.copy(channelCount = detectedCount)
                db.cameraDao().insertCamera(updatedCam)
                currentCamera = updatedCam
                isProbing = false
            }
        }
    }

    // --- EDIT CAMERA DIALOG ---
    if (showEditDialog) {
        var editName by remember { mutableStateOf(currentCamera.name) }
        var editIp by remember { mutableStateOf(currentCamera.ip) }
        var editPort by remember { mutableStateOf(currentCamera.port.toString()) }
        var editUser by remember { mutableStateOf(currentCamera.username) }
        var editPass by remember { mutableStateOf(currentCamera.password) }
        var editType by remember { mutableStateOf(currentCamera.type) }
        var editChannels by remember { mutableIntStateOf(currentCamera.channelCount) }
        var editStreamType by remember { mutableStateOf(currentCamera.streamType) }
        var editCustomPath by remember { mutableStateOf(currentCamera.customPath) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Camera / DVR Settings") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Device Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editIp,
                            onValueChange = { editIp = it },
                            label = { Text("IP Address") },
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = editPort,
                            onValueChange = { editPort = it.filter { c -> c.isDigit() } },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Brand Selector (TV D-Pad Friendly Dialog)
                    TvOptionSelectorField(
                        label = "Brand / Protocol",
                        selectedValue = editType,
                        options = DeviceUtils.DEVICE_TYPES,
                        onOptionSelected = { type ->
                            editType = type
                        }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editUser,
                            onValueChange = { editUser = it },
                            label = { Text("Username") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editPass,
                            onValueChange = { editPass = it },
                            label = { Text("Password") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editChannels.toString(),
                            onValueChange = { editChannels = it.toIntOrNull() ?: 1 },
                            label = { Text("Channels Count") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Stream Resolution Quality:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (editStreamType == "sub"),
                                onClick = { editStreamType = "sub" }
                            )
                            Text("Substream (SD)", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (editStreamType == "main"),
                                onClick = { editStreamType = "main" }
                            )
                            Text("Mainstream (HD)", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedTextField(
                        value = editCustomPath,
                        onValueChange = { editCustomPath = it },
                        label = { Text("Custom RTSP Path (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = currentCamera.copy(
                        name = editName.trim().ifEmpty { currentCamera.name },
                        ip = editIp.trim(),
                        port = editPort.toIntOrNull() ?: 554,
                        username = editUser.trim(),
                        password = editPass,
                        type = editType,
                        channelCount = editChannels.coerceAtLeast(1),
                        streamType = editStreamType,
                        customPath = editCustomPath.trim()
                    )
                    scope.launch {
                        db.cameraDao().insertCamera(updated)
                        currentCamera = updated
                        channelCount = updated.channelCount
                        selectedStreamType = updated.streamType
                        showEditDialog = false
                    }
                }) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Camera?") },
            text = { Text("Are you sure you want to remove '${currentCamera.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            db.cameraDao().deleteCamera(currentCamera)
                            showDeleteDialog = false
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
                        Text(currentCamera.name)
                        Text(
                            text = if (isProbing) "Detecting channels..." else "${currentCamera.type} • ${currentCamera.ip}:${currentCamera.port}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    // --- EDIT BUTTON ---
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, "Edit Device Settings")
                    }
                    // --- DELETE BUTTON ---
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete Device", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            if (selectedChannels.isNotEmpty()) {
                val watchInteraction = remember { MutableInteractionSource() }
                val isWatchFocused by watchInteraction.collectIsFocusedAsState()

                Button(
                    onClick = {
                        val camToPlay = currentCamera.copy(streamType = selectedStreamType)
                        val urls = selectedChannels.sorted().map { ch ->
                            DeviceUtils.generateUrlForChannel(camToPlay, ch)
                        }
                        onPlaySelected(urls)
                    },
                    interactionSource = watchInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp)
                        .focusable(interactionSource = watchInteraction)
                        .border(
                            width = if (isWatchFocused) 3.5.dp else 0.dp,
                            color = if (isWatchFocused) Color(0xFFFFD700) else Color.Transparent,
                            shape = RoundedCornerShape(25.dp)
                        )
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Watch Selected (${selectedChannels.size})", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            if (isProbing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Stream Quality Selector & Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Stream Quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = (selectedStreamType == "sub"),
                            onClick = { selectedStreamType = "sub" },
                            label = { Text("Substream (SD)") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = (selectedStreamType == "main"),
                            onClick = { selectedStreamType = "main" },
                            label = { Text("Mainstream (HD)") }
                        )
                    }
                }

                if (channelCount > 1) {
                    TextButton(
                        onClick = {
                            if (selectedChannels.size == channelCount) {
                                selectedChannels.clear()
                            } else {
                                selectedChannels.clear()
                                for (ch in 1..channelCount) selectedChannels.add(ch)
                            }
                        }
                    ) {
                        Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedChannels.size == channelCount) "Deselect All" else "Select All")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Available Channels", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))

            // Grid of Channels
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 75.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                width = if (isFocused) 3.5.dp else 0.dp,
                color = if (isFocused) Color(0xFFFFD700) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "CH $num",
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}