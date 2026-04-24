package com.example.lancamapp

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.lancamapp.database.AppDatabase
import com.example.lancamapp.database.CameraEntity
import com.example.lancamapp.database.FavoriteGrid
import com.example.lancamapp.database.FavoriteGridSlot
import com.example.lancamapp.utils.DeviceUtils
import com.example.lancamapp.utils.VlcManager
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

data class StreamSlot(
    val camera: CameraEntity,
    val channel: Int
)

class MultiCameraLiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MultiCameraLiveScreen()
        }
    }
}

@Composable
fun MultiCameraLiveScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val cameraList by db.cameraDao().getAllCameras().collectAsState(initial = emptyList())
    val favoriteGrids by db.cameraDao().getAllFavoriteGrids().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var maxSlots by remember { mutableIntStateOf(4) }
    val activeSlots = remember { mutableStateListOf<StreamSlot?>() }

    // Initialize slots if empty
    LaunchedEffect(maxSlots) {
        while (activeSlots.size < maxSlots) {
            activeSlots.add(null)
        }
        while (activeSlots.size > maxSlots) {
            activeSlots.removeAt(activeSlots.size - 1)
        }
    }

    var showAddDialogForIndex by remember { mutableStateOf<Int?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSaveFavoriteDialog by remember { mutableStateOf(false) }
    var showLoadFavoriteDialog by remember { mutableStateOf(false) }
    var focusedSlot by remember { mutableStateOf<StreamSlot?>(null) }

    BackHandler(enabled = focusedSlot != null) {
        focusedSlot = null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val parentWidth = constraints.maxWidth
        val parentHeight = constraints.maxHeight

        if (focusedSlot != null) {
            val url = DeviceUtils.generateUrlForChannel(focusedSlot!!.camera, focusedSlot!!.channel)
            MultiCameraVlcPlayer(
                url = url,
                modifier = Modifier.fillMaxSize(),
                onDoubleTap = { focusedSlot = null }
            )
            IconButton(
                onClick = { focusedSlot = null },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        } else {
            val (rows, cols) = remember(maxSlots, parentWidth, parentHeight) {
                calculateOptimalGrid(maxSlots, parentWidth, parentHeight)
            }

            Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0 until rows) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until cols) {
                            val index = r * cols + c
                            if (index < maxSlots) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                                    val slot = activeSlots.getOrNull(index)
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isFocused by interactionSource.collectIsFocusedAsState()

                                    if (slot != null) {
                                        val url = DeviceUtils.generateUrlForChannel(slot.camera, slot.channel)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(
                                                    width = if (isFocused) 4.dp else 0.dp,
                                                    color = if (isFocused) Color.Yellow else Color.Transparent,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .focusable(interactionSource = interactionSource)
                                                .clickable(interactionSource = interactionSource, indication = null) {
                                                    focusedSlot = slot
                                                }
                                        ) {
                                            MultiCameraVlcPlayer(
                                                url = url,
                                                modifier = Modifier.fillMaxSize(),
                                                parentSize = IntSize(parentWidth / cols, parentHeight / rows),
                                                onDoubleTap = { focusedSlot = slot }
                                            )
                                            IconButton(
                                                onClick = { activeSlots[index] = null },
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White.copy(alpha = 0.5f))
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    if (isFocused) Color.White.copy(alpha = 0.2f)
                                                    else Color.DarkGray.copy(alpha = 0.3f)
                                                )
                                                .border(
                                                    width = if (isFocused) 4.dp else 0.dp,
                                                    color = if (isFocused) Color.Yellow else Color.Transparent,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .focusable(interactionSource = interactionSource)
                                                .clickable(interactionSource = interactionSource, indication = null) {
                                                    showAddDialogForIndex = index
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Add, 
                                                contentDescription = "Add Channel", 
                                                tint = if (isFocused) Color.Yellow else Color.White
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Empty spacer to maintain grid balance
                                Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }

            // Controls overlay
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvControlIconButton(
                    onClick = { showLoadFavoriteDialog = true },
                    icon = Icons.Default.List,
                    contentDescription = "Load Favorite"
                )
                TvControlIconButton(
                    onClick = { showSaveFavoriteDialog = true },
                    icon = Icons.Default.Save,
                    contentDescription = "Save Favorite"
                )
                TvControlIconButton(
                    onClick = { showSettingsDialog = true },
                    icon = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        }

        if (showSaveFavoriteDialog) {
            var favName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showSaveFavoriteDialog = false },
                title = { Text("Save Current Grid") },
                text = {
                    TextField(
                        value = favName,
                        onValueChange = { favName = it },
                        label = { Text("Favorite Name") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val gridId = db.cameraDao().insertFavoriteGrid(FavoriteGrid(name = favName, maxSlots = maxSlots))
                            val slots = activeSlots.mapIndexedNotNull { index, slot ->
                                slot?.let { FavoriteGridSlot(gridId = gridId.toInt(), slotIndex = index, cameraId = it.camera.id, channelNumber = it.channel) }
                            }
                            db.cameraDao().insertFavoriteGridSlots(slots)
                            showSaveFavoriteDialog = false
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveFavoriteDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showLoadFavoriteDialog) {
            Dialog(onDismissRequest = { showLoadFavoriteDialog = false }) {
                Card(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Favorite Grids", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyColumn {
                            items(favoriteGrids) { grid ->
                                ListItem(
                                    headlineContent = { Text(grid.name) },
                                    supportingContent = { Text("${grid.maxSlots} Channels") },
                                    trailingContent = {
                                        IconButton(onClick = { scope.launch { db.cameraDao().deleteFavoriteGrid(grid.id) } }) {
                                            Icon(Icons.Default.Close, contentDescription = "Delete")
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            maxSlots = grid.maxSlots
                                            val savedSlots = db.cameraDao().getSlotsForGrid(grid.id)
                                            activeSlots.clear()
                                            // Fill with nulls first
                                            repeat(maxSlots) { activeSlots.add(null) }
                                            // Populate from DB
                                            savedSlots.forEach { savedSlot ->
                                                val camera = db.cameraDao().getCameraById(savedSlot.cameraId)
                                                if (camera != null && savedSlot.slotIndex < maxSlots) {
                                                    activeSlots[savedSlot.slotIndex] = StreamSlot(camera, savedSlot.channelNumber)
                                                }
                                            }
                                            showLoadFavoriteDialog = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Grid Settings") },
                text = {
                    Column {
                        Text("Number of channels:")
                        val options = listOf(2, 4, 6, 8, 10, 12, 16)
                        options.chunked(4).forEach { rowOptions ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowOptions.forEach { option ->
                                    Row(
                                        modifier = Modifier.weight(1f).clickable { maxSlots = option; showSettingsDialog = false }.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = maxSlots == option, onClick = { maxSlots = option; showSettingsDialog = false })
                                        Text(text = "$option", modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                                if (rowOptions.size < 4) Spacer(modifier = Modifier.weight((4 - rowOptions.size).toFloat()))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) { Text("Close") }
                }
            )
        }

        if (showAddDialogForIndex != null) {
            val index = showAddDialogForIndex!!
            Dialog(onDismissRequest = { showAddDialogForIndex = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Channel", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyColumn {
                            items(cameraList) { camera ->
                                Text(
                                    text = camera.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                for (ch in 1..camera.channelCount) {
                                    ListItem(
                                        headlineContent = { Text("Channel $ch") },
                                        modifier = Modifier.clickable {
                                            activeSlots[index] = StreamSlot(camera, ch)
                                            showAddDialogForIndex = null
                                        }
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvControlIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .background(
                if (isFocused) Color.White.copy(alpha = 0.3f)
                else Color.Black.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.Yellow else Color.Transparent,
                shape = CircleShape
            )
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Yellow else Color.White
        )
    }
}

private fun calculateOptimalGrid(maxSlots: Int, width: Int, height: Int): Pair<Int, Int> {
    if (maxSlots <= 0) return 1 to 1
    
    var bestRows = 1
    var bestCols = maxSlots
    var bestScore = Double.MAX_VALUE
    
    // Target aspect ratio for each cell (4:3 is common for security cameras)
    val targetAspect = 1.33 

    for (rows in 1..maxSlots) {
        val cols = Math.ceil(maxSlots.toDouble() / rows).toInt()
        val cellWidth = width.toDouble() / cols
        val cellHeight = height.toDouble() / rows
        val cellAspect = cellWidth / cellHeight
        
        // We want to minimize the difference between the resulting cell aspect and our target aspect
        val score = Math.abs(cellAspect - targetAspect)
        
        if (score < bestScore) {
            bestScore = score
            bestRows = rows
            bestCols = cols
        }
    }
    
    return bestRows to bestCols
}

@Composable
fun MultiCameraVlcPlayer(url: String, modifier: Modifier, parentSize: IntSize = IntSize.Zero, onDoubleTap: () -> Unit) {
    val context = LocalContext.current
    // Use centralized LibVLC instance
    val libVlc = remember { VlcManager.getLibVLC(context) }
    val mediaPlayer = remember { MediaPlayer(libVlc) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(url) {
        val media = Media(libVlc, Uri.parse(url))
        VlcManager.configureMedia(media, url)

        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()

        kotlinx.coroutines.delay(1000)
        isLoading = false
    }

    DisposableEffect(url) {
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.vlcVout.detachViews()
            mediaPlayer.release()
            // Do NOT release libVlc here as it's shared
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            mediaPlayer.vlcVout.setVideoView(this@apply)
                            if (parentSize != IntSize.Zero) {
                                mediaPlayer.vlcVout.setWindowSize(parentSize.width, parentSize.height)
                            } else {
                                mediaPlayer.vlcVout.setWindowSize(width, height)
                            }
                            mediaPlayer.vlcVout.attachViews()
                            mediaPlayer.aspectRatio = null
                            mediaPlayer.scale = 0f
                        }
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
                            if (parentSize != IntSize.Zero) {
                                mediaPlayer.vlcVout.setWindowSize(parentSize.width, parentSize.height)
                            } else {
                                mediaPlayer.vlcVout.setWindowSize(w, ht)
                            }
                        }
                        override fun surfaceDestroyed(h: SurfaceHolder) { mediaPlayer.vlcVout.detachViews() }
                    })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                },
            update = { view ->
                // Ensure the video scales when the parent container size changes
                if (parentSize != IntSize.Zero) {
                    mediaPlayer.vlcVout.setWindowSize(parentSize.width, parentSize.height)
                }
            }
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}
