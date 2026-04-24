package com.example.lancamapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    onBack: () -> Unit // <--- ADDED THIS PARAMETER
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    // Form State
    var name by remember { mutableStateOf("My Camera") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DeviceUtils.DEVICE_TYPES[0]) }
    var expanded by remember { mutableStateOf(false) }
    var channel by remember { mutableStateOf("1") }

    // Auto-select Type based on scan hint
    LaunchedEffect(Unit) {
        if (scannedType.contains("CP Plus")) selectedType = "CP Plus / Dahua"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Camera") },
                navigationIcon = {
                    // <--- ADDED BACK BUTTON
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            Text("IP Address: $scannedIp", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Name Field
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Camera Name") }, modifier = Modifier.fillMaxWidth())

            // Type Dropdown
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedTextField(
                    value = selectedType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Device Brand") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, "drop") },
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true }
                )
                // Type Dropdown (Using ExposedDropdownMenuBox for reliable clicking)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Device Brand") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor() // <--- CRITICAL: Binds the menu to this text field
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DeviceUtils.DEVICE_TYPES.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }


            }

            // Creds
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("User") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = {
                    scope.launch {
                        val newCam = CameraEntity(
                            name = name,
                            ip = scannedIp,
                            username = username,
                            password = password,
                            type = selectedType,
                            channelCount = 0
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