package com.example.lancamapp

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lancamapp.utils.VlcManager
import kotlinx.coroutines.delay
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class MultiViewPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val urls = intent.getStringArrayListExtra("URL_LIST") ?: arrayListOf()
        setContent { ResponsiveMatrixScreen(urls) }
    }
}

@Composable
fun ResponsiveMatrixScreen(urls: List<String>) {
    val context = LocalContext.current
    var focusedUrl by remember { mutableStateOf<String?>(null) }
    var unmutedUrl by remember { mutableStateOf<String?>(null) }
    var isAllMuted by remember { mutableStateOf(true) }

    BackHandler(enabled = focusedUrl != null) {
        focusedUrl = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (focusedUrl != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                ZoomablePlayerBox(
                    url = focusedUrl!!,
                    onDoubleTap = { focusedUrl = null }
                )

                // TV Remote Friendly Exit Fullscreen Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Fullscreen Live Camera Stream",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    val exitInteraction = remember { MutableInteractionSource() }
                    val isExitFocused by exitInteraction.collectIsFocusedAsState()

                    Surface(
                        onClick = { focusedUrl = null },
                        interactionSource = exitInteraction,
                        shape = RoundedCornerShape(20.dp),
                        color = if (isExitFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.2f),
                        border = BorderStroke(
                            width = if (isExitFocused) 3.dp else 1.dp,
                            color = if (isExitFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.focusable(interactionSource = exitInteraction)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.FullscreenExit,
                                contentDescription = "Exit",
                                tint = if (isExitFocused) Color.Black else Color.White
                            )
                            Text(
                                "Exit Fullscreen",
                                color = if (isExitFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            val count = urls.size
            val (rows, cols) = when (count) {
                1 -> 1 to 1
                2 -> 1 to 2
                3, 4 -> 2 to 2
                5, 6 -> 2 to 3
                7, 8 -> 2 to 4
                else -> 3 to 3
            }

            Column(modifier = Modifier.fillMaxSize()) {

                // --- TOP CONTROL BAR FOR TV REMOTE NAVIGATION ---
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
                                text = "Multi-View Player",
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
                                    text = "$count Stream(s)",
                                    color = Color(0xFFFFD700),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Mute / Unmute All Button
                            val muteInteraction = remember { MutableInteractionSource() }
                            val isMuteFocused by muteInteraction.collectIsFocusedAsState()

                            Surface(
                                onClick = {
                                    isAllMuted = !isAllMuted
                                    if (isAllMuted) unmutedUrl = null
                                },
                                interactionSource = muteInteraction,
                                shape = RoundedCornerShape(20.dp),
                                color = if (isMuteFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.15f),
                                border = BorderStroke(
                                    width = if (isMuteFocused) 3.dp else 1.dp,
                                    color = if (isMuteFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.focusable(interactionSource = muteInteraction)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAllMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute Toggle",
                                        tint = if (isMuteFocused) Color.Black else if (!isAllMuted) Color(0xFFFFD700) else Color.White
                                    )
                                    Text(
                                        text = if (isAllMuted) "Audio Muted" else "Audio ON",
                                        color = if (isMuteFocused) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Close / Back Button
                            val closeInteraction = remember { MutableInteractionSource() }
                            val isCloseFocused by closeInteraction.collectIsFocusedAsState()

                            Surface(
                                onClick = { (context as? Activity)?.finish() },
                                interactionSource = closeInteraction,
                                shape = RoundedCornerShape(20.dp),
                                color = if (isCloseFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.15f),
                                border = BorderStroke(
                                    width = if (isCloseFocused) 3.dp else 1.dp,
                                    color = if (isCloseFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.focusable(interactionSource = closeInteraction)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = if (isCloseFocused) Color.Black else Color.White
                                    )
                                    Text(
                                        "Exit Player",
                                        color = if (isCloseFocused) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // --- MAIN MULTI-STREAM GRID ---
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (r in 0 until rows) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            for (c in 0 until cols) {
                                val index = r * cols + c
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                                    if (index < count) {
                                        val currentUrl = urls[index]
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isFocused by interactionSource.collectIsFocusedAsState()
                                        val isThisTileUnmuted = !isAllMuted && (unmutedUrl == currentUrl || count == 1)

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
                                                    focusedUrl = currentUrl
                                                }
                                        ) {
                                            SingleVlcPlayer(
                                                url = currentUrl,
                                                isMuted = !isThisTileUnmuted,
                                                modifier = Modifier.fillMaxSize(),
                                                onDoubleTap = { focusedUrl = currentUrl }
                                            )

                                            // Top-Left Stream Badge
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.70f),
                                                shape = RoundedCornerShape(bottomEnd = 8.dp),
                                                modifier = Modifier.align(Alignment.TopStart)
                                            ) {
                                                Text(
                                                    text = "Stream ${index + 1}",
                                                    color = if (isFocused) Color(0xFFFFD700) else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }

                                            // Bottom-Left Audio Indicator
                                            val audioBtnInteraction = remember { MutableInteractionSource() }
                                            val isAudioBtnFocused by audioBtnInteraction.collectIsFocusedAsState()

                                            IconButton(
                                                onClick = {
                                                    isAllMuted = false
                                                    unmutedUrl = if (isThisTileUnmuted) null else currentUrl
                                                },
                                                interactionSource = audioBtnInteraction,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(4.dp)
                                                    .focusable(interactionSource = audioBtnInteraction)
                                                    .background(
                                                        if (isAudioBtnFocused) Color(0xFFFFD700) else Color.Black.copy(alpha = 0.6f),
                                                        shape = CircleShape
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = if (isThisTileUnmuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                                    contentDescription = "Toggle Audio",
                                                    tint = if (isAudioBtnFocused) Color.Black else if (isThisTileUnmuted) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.fillMaxSize().background(Color.Black))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomablePlayerBox(url: String, onDoubleTap: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        val maxX = (size.width * (scale - 1)) / 2
                        val maxY = (size.height * (scale - 1)) / 2
                        offset = androidx.compose.ui.geometry.Offset(
                            x = (offset.x + pan.x * scale).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y * scale).coerceIn(-maxY, maxY)
                        )
                    } else {
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            }
    ) {
        SingleVlcPlayer(
            url = url,
            isMuted = false,
            modifier = Modifier.fillMaxSize(),
            onDoubleTap = {
                scale = 1f
                offset = androidx.compose.ui.geometry.Offset.Zero
                onDoubleTap()
            }
        )
    }
}

@Composable
fun SingleVlcPlayer(url: String, isMuted: Boolean = true, modifier: Modifier, onDoubleTap: () -> Unit) {
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
                    layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            mediaPlayer.vlcVout.setVideoView(this@apply)
                            mediaPlayer.vlcVout.setWindowSize(width, height)
                            mediaPlayer.vlcVout.attachViews()
                            mediaPlayer.aspectRatio = null
                            mediaPlayer.scale = 0f
                        }
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
                        override fun surfaceDestroyed(h: SurfaceHolder) { mediaPlayer.vlcVout.detachViews() }
                    })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                }
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFFFD700))
        }
    }
}