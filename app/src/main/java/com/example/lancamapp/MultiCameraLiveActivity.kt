package com.example.lancamapp

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.lancamapp.database.AppDatabase
import com.example.lancamapp.database.CameraEntity
import com.example.lancamapp.database.FavoriteGrid
import com.example.lancamapp.database.FavoriteGridSlot
import com.example.lancamapp.utils.DeviceUtils
import com.example.lancamapp.utils.VlcManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

data class StreamSlot(
    val camera: CameraEntity,
    val channel: Int
)

class MultiCameraLiveActivity : ComponentActivity() {
    private var openSettingsLambda: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MultiCameraLiveScreen(onRegisterMenuAction = { openSettingsLambda = it })
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            openSettingsLambda?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun MultiCameraLiveScreen(onRegisterMenuAction: (() -> Unit) -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val cameraList by db.cameraDao().getAllCameras().collectAsState(initial = emptyList())
    val favoriteGrids by db.cameraDao().getAllFavoriteGrids().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var maxSlots by remember { mutableIntStateOf(4) }
    val activeSlots = remember { mutableStateListOf<StreamSlot?>() }
    var unmutedSlotIndex by remember { mutableIntStateOf(-1) }

    // Initialize/resize slots array
    LaunchedEffect(maxSlots) {
        while (activeSlots.size < maxSlots) {
            activeSlots.add(null)
        }
        while (activeSlots.size > maxSlots) {
            activeSlots.removeAt(activeSlots.size - 1)
        }
        if (unmutedSlotIndex >= maxSlots) {
            unmutedSlotIndex = -1
        }
    }

    var showAddDialogForIndex by remember { mutableStateOf<Int?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSaveFavoriteDialog by remember { mutableStateOf(false) }
    var showLoadFavoriteDialog by remember { mutableStateOf(false) }
    var focusedSlot by remember { mutableStateOf<StreamSlot?>(null) }
    var isFullscreenMuted by remember { mutableStateOf(false) }
    var isStretchAspectRatio by remember { mutableStateOf(true) }
    var showPtzControls by remember { mutableStateOf(false) }
    var currentSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var streamReloadKey by remember { mutableIntStateOf(0) }

    // Register hardware menu callback
    LaunchedEffect(Unit) {
        onRegisterMenuAction {
            showSettingsDialog = true
        }
    }

    // Auto-fill all cameras helper
    fun autoFillAllChannels() {
        var slotIdx = 0
        cameraList.forEach { camera ->
            val count = camera.channelCount.coerceAtLeast(1)
            for (ch in 1..count) {
                if (slotIdx < maxSlots) {
                    activeSlots[slotIdx] = StreamSlot(camera, ch)
                    slotIdx++
                }
            }
        }
    }

    var backPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        when {
            showPtzControls -> showPtzControls = false
            focusedSlot != null -> focusedSlot = null
            showSettingsDialog -> showSettingsDialog = false
            showSaveFavoriteDialog -> showSaveFavoriteDialog = false
            showLoadFavoriteDialog -> showLoadFavoriteDialog = false
            showAddDialogForIndex != null -> showAddDialogForIndex = null
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(context, "Press back again to exit Live Matrix", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val parentWidth = constraints.maxWidth
        val parentHeight = constraints.maxHeight

        if (focusedSlot != null) {
            // Fullscreen Single Camera View
            val currentSlot = focusedSlot!!
            val url = DeviceUtils.generateUrlForChannel(currentSlot.camera, currentSlot.channel)

            Box(modifier = Modifier.fillMaxSize()) {
                key(streamReloadKey) {
                    MultiCameraVlcPlayer(
                        url = url,
                        modifier = Modifier.fillMaxSize(),
                        isMuted = isFullscreenMuted,
                        isStretch = isStretchAspectRatio,
                        onSurfaceCreated = { surface -> currentSurfaceView = surface },
                        onDoubleTap = { focusedSlot = null }
                    )
                }

                // Fullscreen top overlay header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${currentSlot.camera.name} — Channel ${currentSlot.channel}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${currentSlot.camera.type} • ${currentSlot.camera.ip}:${currentSlot.camera.port}",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Snapshot Button
                        TvControlBarButton(
                            onClick = {
                                currentSurfaceView?.let { surface ->
                                    com.example.lancamapp.utils.SnapshotUtils.captureSurfaceSnapshot(
                                        surfaceView = surface,
                                        context = context,
                                        cameraName = currentSlot.camera.name,
                                        onSuccess = { fileName ->
                                            Toast.makeText(context, "📸 Snapshot saved: $fileName", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { errorMsg ->
                                            Toast.makeText(context, "Snapshot error: $errorMsg", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } ?: run {
                                    Toast.makeText(context, "Stream initializing...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            icon = Icons.Default.PhotoCamera,
                            label = "Snapshot"
                        )

                        // PTZ Control Toggle
                        TvControlBarButton(
                            onClick = { showPtzControls = !showPtzControls },
                            icon = Icons.Default.ControlCamera,
                            label = "PTZ",
                            tint = if (showPtzControls) Color(0xFFFFD700) else Color.White
                        )

                        // Reconnect
                        TvControlBarButton(
                            onClick = {
                                streamReloadKey++
                                Toast.makeText(context, "Reconnecting stream...", Toast.LENGTH_SHORT).show()
                            },
                            icon = Icons.Default.Refresh,
                            label = "Reload"
                        )

                        // Aspect Ratio Toggle
                        TvControlBarButton(
                            onClick = { isStretchAspectRatio = !isStretchAspectRatio },
                            icon = Icons.Default.AspectRatio,
                            label = if (isStretchAspectRatio) "Stretch" else "16:9",
                            tint = if (isStretchAspectRatio) Color(0xFFFFD700) else Color.White
                        )

                        // Audio Mute/Unmute
                        TvControlBarButton(
                            onClick = { isFullscreenMuted = !isFullscreenMuted },
                            icon = if (isFullscreenMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            label = if (isFullscreenMuted) "Muted" else "Audio ON",
                            tint = if (!isFullscreenMuted) Color(0xFFFFD700) else Color.White
                        )

                        // Close Fullscreen
                        TvControlBarButton(
                            onClick = { focusedSlot = null },
                            icon = Icons.Default.FullscreenExit,
                            label = "Exit Fullscreen"
                        )
                    }
                }

                // PTZ On-Screen Control Pad Overlay
                if (showPtzControls) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.80f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "PTZ Controls",
                                color = Color(0xFFFFD700),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            TvControlIconButton(
                                onClick = { Toast.makeText(context, "PTZ: Tilt Up", Toast.LENGTH_SHORT).show() },
                                icon = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Tilt Up"
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TvControlIconButton(
                                    onClick = { Toast.makeText(context, "PTZ: Pan Left", Toast.LENGTH_SHORT).show() },
                                    icon = Icons.Default.KeyboardArrowLeft,
                                    contentDescription = "Pan Left"
                                )

                                TvControlIconButton(
                                    onClick = { Toast.makeText(context, "PTZ: Center", Toast.LENGTH_SHORT).show() },
                                    icon = Icons.Default.CenterFocusStrong,
                                    contentDescription = "Center"
                                )

                                TvControlIconButton(
                                    onClick = { Toast.makeText(context, "PTZ: Pan Right", Toast.LENGTH_SHORT).show() },
                                    icon = Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Pan Right"
                                )
                            }

                            TvControlIconButton(
                                onClick = { Toast.makeText(context, "PTZ: Tilt Down", Toast.LENGTH_SHORT).show() },
                                icon = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Tilt Down"
                            )
                        }
                    }
                }
            }
        } else {
            // Matrix Grid View (Structured Top Control Header Bar + Stream Grid)
            val (rows, cols) = remember(maxSlots, parentWidth, parentHeight) {
                calculateOptimalGrid(maxSlots, parentWidth, parentHeight)
            }

            val activeCount = activeSlots.count { it != null }

            Column(modifier = Modifier.fillMaxSize()) {

                // --- TV REMOTE FRIENDLY TOP CONTROL BAR ---
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Live Matrix",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                color = Color(0xFFFFD700).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "$activeCount / $maxSlots Active Feeds",
                                    color = Color(0xFFFFD700),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // D-Pad Remote Focusable Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvControlBarButton(
                                onClick = { autoFillAllChannels() },
                                icon = Icons.Default.AutoMode,
                                label = "Auto Fill"
                            )
                            TvControlBarButton(
                                onClick = { showSettingsDialog = true },
                                icon = Icons.Default.Settings,
                                label = "Layout ($maxSlots Grid)"
                            )
                            TvControlBarButton(
                                onClick = { showLoadFavoriteDialog = true },
                                icon = Icons.AutoMirrored.Filled.List,
                                label = "Presets"
                            )
                            TvControlBarButton(
                                onClick = { showSaveFavoriteDialog = true },
                                icon = Icons.Default.Save,
                                label = "Save Grid"
                            )
                            TvControlBarButton(
                                onClick = {
                                    for (i in activeSlots.indices) activeSlots[i] = null
                                    unmutedSlotIndex = -1
                                },
                                icon = Icons.Default.DeleteSweep,
                                label = "Clear All"
                            )
                        }
                    }
                }

                // --- MAIN CAMERA STREAM GRID ---
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (r in 0 until rows) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            for (c in 0 until cols) {
                                val index = r * cols + c
                                if (index < maxSlots) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(1.dp)
                                    ) {
                                        val slot = activeSlots.getOrNull(index)
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isFocused by interactionSource.collectIsFocusedAsState()

                                        if (slot != null) {
                                            val url = DeviceUtils.generateUrlForChannel(slot.camera, slot.channel)
                                            val isThisTileUnmuted = (unmutedSlotIndex == index)

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .border(
                                                        width = if (isFocused) 3.5.dp else 0.dp,
                                                        color = if (isFocused) Color(0xFFFFD700) else Color.Transparent,
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
                                                    isMuted = !isThisTileUnmuted,
                                                    isStretch = true,
                                                    onDoubleTap = { focusedSlot = slot }
                                                )

                                                // Top-Left Camera & Channel Badge
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.70f),
                                                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                ) {
                                                    Text(
                                                        text = "${slot.camera.name} [CH ${slot.channel}]",
                                                        color = if (isFocused) Color(0xFFFFD700) else Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }

                                                // Top-Right Action Controls (Audio & Remove)
                                                Row(
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    TvControlIconButton(
                                                        onClick = {
                                                            unmutedSlotIndex = if (isThisTileUnmuted) -1 else index
                                                        },
                                                        icon = if (isThisTileUnmuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                                        contentDescription = "Toggle Audio",
                                                        tint = if (isThisTileUnmuted) Color(0xFFFFD700) else Color.White
                                                    )

                                                    TvControlIconButton(
                                                        onClick = {
                                                            activeSlots[index] = null
                                                            if (unmutedSlotIndex == index) unmutedSlotIndex = -1
                                                        },
                                                        icon = Icons.Default.Close,
                                                        contentDescription = "Remove Stream"
                                                    )
                                                }
                                            }
                                        } else {
                                            // Empty Slot with D-Pad Focusable Add Action
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        if (isFocused) Color.White.copy(alpha = 0.22f)
                                                        else Color.DarkGray.copy(alpha = 0.35f)
                                                    )
                                                    .border(
                                                        width = if (isFocused) 3.5.dp else 0.dp,
                                                        color = if (isFocused) Color(0xFFFFD700) else Color.Transparent,
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .focusable(interactionSource = interactionSource)
                                                    .clickable(interactionSource = interactionSource, indication = null) {
                                                        showAddDialogForIndex = index
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = "Add Camera Feed",
                                                        tint = if (isFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Slot ${index + 1}",
                                                        color = if (isFocused) Color(0xFFFFD700) else Color.LightGray,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- SAVE FAVORITE DIALOG ---
        if (showSaveFavoriteDialog) {
            var favName by remember { mutableStateOf("My Grid Preset") }
            AlertDialog(
                onDismissRequest = { showSaveFavoriteDialog = false },
                title = { Text("Save Current Grid Preset") },
                text = {
                    OutlinedTextField(
                        value = favName,
                        onValueChange = { favName = it },
                        label = { Text("Preset Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            val nameToSave = favName.trim().ifEmpty { "Grid ($maxSlots Channels)" }
                            val gridId = db.cameraDao().insertFavoriteGrid(FavoriteGrid(name = nameToSave, maxSlots = maxSlots))
                            val slots = activeSlots.mapIndexedNotNull { index, slot ->
                                slot?.let {
                                    FavoriteGridSlot(
                                        gridId = gridId.toInt(),
                                        slotIndex = index,
                                        cameraId = it.camera.id,
                                        channelNumber = it.channel
                                    )
                                }
                            }
                            db.cameraDao().insertFavoriteGridSlots(slots)
                            showSaveFavoriteDialog = false
                        }
                    }) { Text("Save Preset") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveFavoriteDialog = false }) { Text("Cancel") }
                }
            )
        }

        // --- LOAD FAVORITE DIALOG ---
        if (showLoadFavoriteDialog) {
            Dialog(onDismissRequest = { showLoadFavoriteDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.75f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Saved Grid Presets", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (favoriteGrids.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No saved presets yet.")
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(favoriteGrids) { grid ->
                                    val itemInteraction = remember { MutableInteractionSource() }
                                    val isItemFocused by itemInteraction.collectIsFocusedAsState()

                                    ListItem(
                                        headlineContent = { Text(grid.name, fontWeight = FontWeight.SemiBold) },
                                        supportingContent = { Text("${grid.maxSlots} Channels Grid") },
                                        trailingContent = {
                                            IconButton(onClick = { scope.launch { db.cameraDao().deleteFavoriteGrid(grid.id) } }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(vertical = 2.dp)
                                            .focusable(interactionSource = itemInteraction)
                                            .border(
                                                width = if (isItemFocused) 3.dp else 0.dp,
                                                color = if (isItemFocused) Color(0xFFFFD700) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable(interactionSource = itemInteraction, indication = null) {
                                                scope.launch {
                                                    maxSlots = grid.maxSlots
                                                    val savedSlots = db.cameraDao().getSlotsForGrid(grid.id)
                                                    activeSlots.clear()
                                                    repeat(maxSlots) { activeSlots.add(null) }
                                                    savedSlots.forEach { savedSlot ->
                                                        val camera = db.cameraDao().getCameraById(savedSlot.cameraId)
                                                        if (camera != null && savedSlot.slotIndex < maxSlots) {
                                                            activeSlots[savedSlot.slotIndex] = StreamSlot(camera, savedSlot.channelNumber)
                                                        }
                                                    }
                                                    unmutedSlotIndex = -1
                                                    showLoadFavoriteDialog = false
                                                }
                                            }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showLoadFavoriteDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Close") }
                    }
                }
            }
        }

        // --- GRID SETTINGS / LAYOUT SWITCHER DIALOG ---
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Matrix Grid Layout") },
                text = {
                    Column {
                        Text("Select layout / number of visible streams:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        val layoutOptions = listOf(
                            1 to "1 Stream (Single)",
                            2 to "2 Streams (Split)",
                            4 to "4 Streams (2x2 Quad)",
                            6 to "6 Streams (3x2 Matrix)",
                            8 to "8 Streams (4x2 Matrix)",
                            9 to "9 Streams (3x3 Matrix)",
                            16 to "16 Streams (4x4 Matrix)"
                        )

                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                            items(layoutOptions) { (option, label) ->
                                val radioInteraction = remember { MutableInteractionSource() }
                                val isRadioFocused by radioInteraction.collectIsFocusedAsState()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .focusable(interactionSource = radioInteraction)
                                        .border(
                                            width = if (isRadioFocused) 3.dp else 0.dp,
                                            color = if (isRadioFocused) Color(0xFFFFD700) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable(interactionSource = radioInteraction, indication = null) {
                                            maxSlots = option
                                            showSettingsDialog = false
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (maxSlots == option),
                                        onClick = {
                                            maxSlots = option
                                            showSettingsDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = label, fontWeight = if (maxSlots == option) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) { Text("Close") }
                }
            )
        }

        // --- CHANNEL PICKER DIALOG ---
        if (showAddDialogForIndex != null) {
            val index = showAddDialogForIndex!!
            Dialog(onDismissRequest = { showAddDialogForIndex = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Camera Channel", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (cameraList.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No cameras added yet. Please add a camera first.")
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(cameraList) { camera ->
                                    Text(
                                        text = "${camera.name} (${camera.type} • ${camera.ip})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    val count = camera.channelCount.coerceAtLeast(1)
                                    for (ch in 1..count) {
                                        val chInteraction = remember { MutableInteractionSource() }
                                        val isChFocused by chInteraction.collectIsFocusedAsState()

                                        ListItem(
                                            headlineContent = { Text("Channel $ch") },
                                            modifier = Modifier
                                                .padding(vertical = 2.dp)
                                                .focusable(interactionSource = chInteraction)
                                                .border(
                                                    width = if (isChFocused) 3.dp else 0.dp,
                                                    color = if (isChFocused) Color(0xFFFFD700) else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable(interactionSource = chInteraction, indication = null) {
                                                    activeSlots[index] = StreamSlot(camera, ch)
                                                    showAddDialogForIndex = null
                                                }
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showAddDialogForIndex = null },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
fun TvControlBarButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    tint: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(20.dp),
        color = if (isFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.15f),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.Black else tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TvControlIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .focusable(interactionSource = interactionSource)
            .background(
                if (isFocused) Color(0xFFFFD700)
                else Color.Black.copy(alpha = 0.65f),
                shape = CircleShape
            )
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color(0xFFFFD700) else Color.Transparent,
                shape = CircleShape
            )
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else tint
        )
    }
}

private fun calculateOptimalGrid(maxSlots: Int, width: Int, height: Int): Pair<Int, Int> {
    if (maxSlots <= 0) return 1 to 1

    return when (maxSlots) {
        1 -> 1 to 1
        2 -> if (width >= height) 1 to 2 else 2 to 1
        3, 4 -> 2 to 2
        5, 6 -> 2 to 3
        7, 8 -> 2 to 4
        9 -> 3 to 3
        10, 11, 12 -> 3 to 4
        else -> 4 to 4
    }
}

@Composable
fun MultiCameraVlcPlayer(
    url: String,
    modifier: Modifier,
    parentSize: IntSize = IntSize.Zero,
    isMuted: Boolean = true,
    isStretch: Boolean = true,
    onSurfaceCreated: ((SurfaceView) -> Unit)? = null,
    onDoubleTap: () -> Unit
) {
    val context = LocalContext.current
    val libVlc = remember { VlcManager.getLibVLC(context) }
    val mediaPlayer = remember { MediaPlayer(libVlc) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(url) {
        val media = Media(libVlc, Uri.parse(url))
        VlcManager.configureMedia(media, url)

        mediaPlayer.media = media
        media.release()
        mediaPlayer.volume = if (isMuted) 0 else 100
        mediaPlayer.play()

        delay(1000)
        isLoading = false
    }

    LaunchedEffect(isMuted) {
        mediaPlayer.volume = if (isMuted) 0 else 100
    }

    LaunchedEffect(isStretch) {
        if (isStretch) {
            mediaPlayer.aspectRatio = null
            mediaPlayer.scale = 0f
        } else {
            mediaPlayer.aspectRatio = "16:9"
            mediaPlayer.scale = 1f
        }
    }

    DisposableEffect(url) {
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.vlcVout.detachViews()
            mediaPlayer.release()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            mediaPlayer.vlcVout.setVideoView(this@apply)
                            if (parentSize != IntSize.Zero) {
                                mediaPlayer.vlcVout.setWindowSize(parentSize.width, parentSize.height)
                            } else {
                                mediaPlayer.vlcVout.setWindowSize(width, height)
                            }
                            mediaPlayer.vlcVout.attachViews()
                            if (isStretch) {
                                mediaPlayer.aspectRatio = null
                                mediaPlayer.scale = 0f
                            } else {
                                mediaPlayer.aspectRatio = "16:9"
                                mediaPlayer.scale = 1f
                            }
                            onSurfaceCreated?.invoke(this@apply)
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
                if (parentSize != IntSize.Zero) {
                    mediaPlayer.vlcVout.setWindowSize(parentSize.width, parentSize.height)
                }
            }
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFFFD700)
            )
        }
    }
}
