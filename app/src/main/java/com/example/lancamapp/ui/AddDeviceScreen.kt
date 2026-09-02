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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    scannedIp: String,
    scannedType: String,
    onSaveComplete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    // Form State
    var ipAddress by remember { mutableStateOf(scannedIp) }
    var name by remember { mutableStateOf(if (scannedType.isNotBlank() && scannedType != "Manual Camera") scannedType else "My Camera") }
    var portStr by remember { mutableStateOf("554") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DeviceUtils.DEVICE_TYPES[0]) }

    val channelOptions = listOf("1 Channel (Single Camera)", "2 Channels", "4 Channels (DVR/NVR)", "8 Channels (DVR/NVR)", "16 Channels (DVR/NVR)", "32 Channels (DVR/NVR)")
    var selectedChannelText by remember { mutableStateOf("4 Channels (DVR/NVR)") }

    var isMainStream by remember { mutableStateOf(false) } // False = Substream (SD), True = Mainstream (HD)
    var customPath by remember { mutableStateOf("") }

    // Auto-select Type and Channel Count based on scan hint
    LaunchedEffect(scannedType) {
        when {
            scannedType.contains("Ezycam") || scannedType.contains("CP Plus Ezycam") -> {
                selectedType = "CP Plus Ezycam"
                selectedChannelText = "1 Channel (Single Camera)"
                name = "CP Plus Ezycam"
            }
            scannedType.contains("CP Plus") || scannedType.contains("Dahua") -> {
                selectedType = "CP Plus / Dahua"
                selectedChannelText = "4 Channels (DVR/NVR)"
                name = "CP Plus DVR"
            }
            scannedType.contains("Hikvision") || scannedType.contains("Prama") -> {
                selectedType = "Hikvision / Prama"
                selectedChannelText = "4 Channels (DVR/NVR)"
                name = "Hikvision DVR"
            }
            scannedType.contains("Tapo") || scannedType.contains("TP-Link") -> {
                selectedType = "Tapo / TP-Link"
                selectedChannelText = "1 Channel (Single Camera)"
                name = "Tapo Camera"
            }
            scannedType.contains("Tiandy") -> {
                selectedType = "Tiandy"
                selectedChannelText = "1 Channel (Single Camera)"
                name = "Tiandy Camera"
            }
            scannedType.contains("Uniview") || scannedType.contains("UNV") -> {
                selectedType = "Uniview / UNV"
                selectedChannelText = "1 Channel (Single Camera)"
                name = "Uniview Camera"
            }
            scannedType.contains("Axis") -> {
                selectedType = "Axis"
                selectedChannelText = "1 Channel (Single Camera)"
                name = "Axis Camera"
            }
            scannedType.contains("Reolink") -> {
                selectedType = "Reolink"
                selectedChannelText = "1 Channel (Single Camera)"
                name = "Reolink Camera"
            }
            scannedType.contains("V380") -> {
                selectedType = "V380 Wi-Fi"
                selectedChannelText = "1 Channel (Single Camera)"
                name = "V380 Camera"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Camera / DVR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // IP Address Field (Fully editable)
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("Camera IP Address (or full RTSP URL)") },
                placeholder = { Text("e.g. 192.168.1.50") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Device Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Brand / Protocol Selector (TV D-Pad Friendly Dialog)
            TvOptionSelectorField(
                label = "Device Brand / Protocol",
                selectedValue = selectedType,
                options = DeviceUtils.DEVICE_TYPES,
                onOptionSelected = { type ->
                    selectedType = type
                    val count = DeviceUtils.probeChannelCount(type)
                    selectedChannelText = if (count == 1) "1 Channel (Single Camera)" else "$count Channels (DVR/NVR)"
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Channel Count & Port in a Row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Channel Count Selector (TV D-Pad Friendly Dialog)
                TvOptionSelectorField(
                    label = "Channels",
                    selectedValue = selectedChannelText,
                    options = channelOptions,
                    onOptionSelected = { selectedChannelText = it },
                    modifier = Modifier.weight(1.5f)
                )

                // Port Field
                OutlinedTextField(
                    value = portStr,
                    onValueChange = { portStr = it.filter { char -> char.isDigit() } },
                    label = { Text("RTSP Port") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Credentials
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    placeholder = { Text("admin") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stream Quality Choice (Substream vs Mainstream)
            Text("Stream Resolution Quality:", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !isMainStream,
                        onClick = { isMainStream = false }
                    )
                    Text("Substream (Faster / SD)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isMainStream,
                        onClick = { isMainStream = true }
                    )
                    Text("Mainstream (HD)", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom RTSP Path (Optional)
            OutlinedTextField(
                value = customPath,
                onValueChange = { customPath = it },
                label = { Text("Custom RTSP Path (Optional, e.g. /live/ch1)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Leave blank for automatic brand path") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = {
                    scope.launch {
                        val parsedPort = portStr.toIntOrNull() ?: 554
                        val cleanIp = ipAddress.trim()
                        val channelCountInt = selectedChannelText.filter { it.isDigit() }.toIntOrNull() ?: 1
                        val newCam = CameraEntity(
                            name = name.trim().ifEmpty { "Camera ($cleanIp)" },
                            ip = cleanIp,
                            port = parsedPort,
                            username = username.trim(),
                            password = password,
                            type = selectedType,
                            channelCount = channelCountInt.coerceAtLeast(1),
                            customPath = customPath.trim(),
                            streamType = if (isMainStream) "main" else "sub"
                        )
                        db.cameraDao().insertCamera(newCam)
                        onSaveComplete()
                    }
                }
            ) {
                Text("SAVE DEVICE")
            }
        }
    }
}

@Composable
fun TvOptionSelectorField(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .focusable(interactionSource = interactionSource)
            .border(
                width = if (isFocused) 3.5.dp else 0.dp,
                color = if (isFocused) Color(0xFFFFD700) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) {
                showDialog = true
            }
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Select") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (isFocused) Color(0xFFFFD700) else MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select $label") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(options) { option ->
                        val itemInteraction = remember { MutableInteractionSource() }
                        val isItemFocused by itemInteraction.collectIsFocusedAsState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .focusable(interactionSource = itemInteraction)
                                .border(
                                    width = if (isItemFocused) 3.dp else 0.dp,
                                    color = if (isItemFocused) Color(0xFFFFD700) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    if (isItemFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(interactionSource = itemInteraction, indication = null) {
                                    onOptionSelected(option)
                                    showDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selectedValue),
                                onClick = {
                                    onOptionSelected(option)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = option,
                                fontWeight = if (option == selectedValue) FontWeight.Bold else FontWeight.Normal,
                                color = if (isItemFocused) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Close") }
            }
        )
    }
}