package com.example.lancamapp

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lancamapp.utils.VlcManager
import org.videolan.libvlc.LibVLC
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Just use fillMaxSize so it uses the whole screen height/width
        Box(modifier = Modifier.fillMaxSize()) {
            MatrixGrid(urls)
        }
    }
}

@Composable
fun MatrixGrid(urls: List<String>) {
    var focusedUrl by remember { mutableStateOf<String?>(null) }

    if (focusedUrl != null) {
        ZoomablePlayerBox(url = focusedUrl!!, onDoubleTap = { focusedUrl = null })
    } else {
        val count = urls.size
        val (rows, cols) = when (count) {
            1 -> 1 to 1
            2 -> 2 to 1 // Stacked vertically looks better in Portrait
            3, 4 -> 2 to 2
            5, 6 -> 3 to 2
            else -> 3 to 3
        }

        // Ensure the Column fills the entire available screen
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until rows) {
                // weight(1f) ensures each row takes equal vertical space
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until cols) {
                        val index = r * cols + c
                        // weight(1f) ensures each cell takes equal horizontal space
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (index < count) {
                                SingleVlcPlayer(
                                    url = urls[index],
                                    modifier = Modifier.fillMaxSize(), // Fill the weight-defined slot
                                    onDoubleTap = { focusedUrl = urls[index] }
                                )
                            } else {
                                // Keeps the grid balanced even if a slot is empty
                                Spacer(modifier = Modifier.fillMaxSize().background(Color.Black))
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
fun SingleVlcPlayer(url: String, modifier: Modifier, onDoubleTap: () -> Unit) {
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

    DisposableEffect(Unit) {
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

                            // Set the display size to match the SurfaceView exactly
                            mediaPlayer.vlcVout.setWindowSize(width, height)

                            mediaPlayer.vlcVout.attachViews()

                            // CRITICAL: Set aspectRatio to null or a blank string to
                            // force it to "Fit Window" (Stretch)
                            mediaPlayer.aspectRatio = null
                            mediaPlayer.scale = 0f // Auto-scale to fit
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
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}

@Composable
fun VerticalDivider() = Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.DarkGray))
@Composable
fun HorizontalDivider() = Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.DarkGray))