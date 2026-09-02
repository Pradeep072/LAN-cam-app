package com.example.lancamapp.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lancamapp.MultiCameraLiveActivity
import com.example.lancamapp.database.AppDatabase
import com.example.lancamapp.database.CameraEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddClick: () -> Unit,
    onCameraClick: (CameraEntity) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }

    // Collect the list of cameras from Room DB as a State
    val cameraList by db.cameraDao().getAllCameras().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Cameras") },
                actions = {
                    val multiViewInteraction = remember { MutableInteractionSource() }
                    val isMultiViewFocused by multiViewInteraction.collectIsFocusedAsState()

                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(context, MultiCameraLiveActivity::class.java)
                            context.startActivity(intent)
                        },
                        interactionSource = multiViewInteraction,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .focusable(interactionSource = multiViewInteraction)
                            .border(
                                width = if (isMultiViewFocused) 3.dp else 0.dp,
                                color = if (isMultiViewFocused) Color(0xFFFFD700) else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Matrix")
                    }

                    val addInteraction = remember { MutableInteractionSource() }
                    val isAddFocused by addInteraction.collectIsFocusedAsState()

                    Button(
                        onClick = onAddClick,
                        interactionSource = addInteraction,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .focusable(interactionSource = addInteraction)
                            .border(
                                width = if (isAddFocused) 3.dp else 0.dp,
                                color = if (isAddFocused) Color(0xFFFFD700) else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = {
                        val intent = Intent(context, MultiCameraLiveActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(bottom = 16.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.GridView, contentDescription = "Multi-View")
                }
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add Camera")
                }
            }
        }
    ) { padding ->
        if (cameraList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No cameras configured yet.", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan LAN for Cameras")
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
                items(cameraList) { camera ->
                    SavedCameraCard(camera = camera, onPlay = { onCameraClick(camera) })
                }
            }
        }
    }
}

@Composable
fun SavedCameraCard(camera: CameraEntity, onPlay: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .focusable(interactionSource = interactionSource)
            .border(
                width = if (isFocused) 3.5.dp else 0.dp,
                color = if (isFocused) Color(0xFFFFD700) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onPlay() },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${camera.type} • ${camera.ip} • ${if (camera.channelCount > 1) "${camera.channelCount} Channels" else "1 Channel"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onPlay,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isFocused) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
            }
        }
    }
}