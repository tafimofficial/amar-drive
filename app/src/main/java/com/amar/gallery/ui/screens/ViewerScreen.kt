package com.amar.gallery.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amar.gallery.data.db.AmarDatabase
import com.amar.gallery.data.model.MediaItem
import com.amar.gallery.data.model.MediaKind
import com.amar.gallery.data.tdlib.TdClientManager
import com.amar.gallery.ui.theme.LocalAppColors
import com.amar.gallery.util.GrowingFileDataSource
import com.amar.gallery.util.NativeEngine
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
fun ViewerScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    db: AmarDatabase,
    tdManager: TdClientManager,
    onClose: () -> Unit
) {
    if (mediaList.isEmpty()) {
        onClose()
        return
    }

    // Intercept back button to return to gallery smoothly
    BackHandler(onBack = onClose)

    val colors = LocalAppColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val safeInitial = initialIndex.coerceIn(0, mediaList.size - 1)
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { mediaList.size }
    )

    var isCurrentZoomed by remember { mutableStateOf(false) }

    val currentItem = mediaList[pagerState.currentPage]
    var isPinned by remember(currentItem.stableId) { mutableStateOf(currentItem.pinned) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Horizontal Swiping Pager for Photos & Videos
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isCurrentZoomed,
            key = { page -> mediaList[page].stableId }
        ) { page ->
            val item = mediaList[page]
            val isCurrentPage = page == pagerState.currentPage

            if (item.kind == MediaKind.VIDEO) {
                VideoPlayerContent(
                    item = item,
                    tdManager = tdManager,
                    isCurrentPage = isCurrentPage,
                    context = context
                )
            } else {
                PhotoViewerContent(
                    item = item,
                    tdManager = tdManager,
                    onZoomStateChanged = { zoomed ->
                        if (isCurrentPage) {
                            isCurrentZoomed = zoomed
                        }
                    }
                )
            }
        }

        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${mediaList.size}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${NativeEngine.formatBytes(currentItem.sizeBytes)} · ${NativeEngine.getDateHeader(currentItem.date)}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.5.sp
                )
            }
        }

        // Left Navigation Button
        if (pagerState.currentPage > 0 && !isCurrentZoomed) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Right Navigation Button
        if (pagerState.currentPage < mediaList.size - 1 && !isCurrentZoomed) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Actions Bar (Save, Share, Pin)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            color = Color.Black.copy(alpha = 0.85f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Save to Gallery
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        val currentPath = tdManager.getFileLocalPath(currentItem.fileId) ?: currentItem.localPath
                        exportToGallery(context, currentItem, currentPath)
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Save", color = Color.White, fontSize = 11.sp)
                }

                // Share
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        val currentPath = tdManager.getFileLocalPath(currentItem.fileId) ?: currentItem.localPath
                        shareMedia(context, currentItem, currentPath)
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Share", color = Color.White, fontSize = 11.sp)
                }

                // Pin Offline
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        val newPinned = db.togglePin(currentItem.chatId, currentItem.messageId)
                        isPinned = newPinned
                        val msg = if (newPinned) "Pinned offline" else "Unpinned"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Pin",
                        tint = if (isPinned) colors.accent else Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isPinned) "Pinned" else "Pin",
                        color = if (isPinned) colors.accent else Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerContent(
    item: MediaItem,
    tdManager: TdClientManager,
    onZoomStateChanged: (Boolean) -> Unit = {}
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale <= 1.05f) {
            scale = 1f
            offset = Offset.Zero
            onZoomStateChanged(false)
        } else {
            scale = newScale
            offset += offsetChange
            onZoomStateChanged(true)
        }
    }

    val context = LocalContext.current
    var localPath by remember(item.fileId) {
        mutableStateOf(tdManager.getFileLocalPath(item.fileId) ?: item.localPath)
    }

    // Trigger full resolution download
    LaunchedEffect(item.fileId) {
        val existing = tdManager.getFileLocalPath(item.fileId) ?: item.localPath
        if (existing == null || !File(existing).exists()) {
            tdManager.downloadFile(item.fileId, priority = 32)
        } else {
            localPath = existing
        }
    }

    // Listen to download updates in real-time
    LaunchedEffect(item.fileId) {
        tdManager.fileEvents.collect { fileId ->
            if (fileId == item.fileId) {
                val updated = tdManager.getFileLocalPath(fileId)
                if (!updated.isNullOrEmpty() && File(updated).exists()) {
                    localPath = updated
                }
            }
        }
    }

    val minithumbBitmap = remember(item.minithumb) {
        NativeEngine.decodeMinithumb(item.minithumb)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(
                state = transformState,
                canPan = { scale > 1.05f }
            ),
        contentAlignment = Alignment.Center
    ) {
        val isOriginalReady = !localPath.isNullOrEmpty() && File(localPath!!).exists()

        if (isOriginalReady) {
            // Full original resolution image
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(localPath!!))
                    .crossfade(true)
                    .build(),
                contentDescription = item.caption ?: item.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        } else if (minithumbBitmap != null) {
            // Instant minithumb placeholder while original loads
            Image(
                bitmap = minithumbBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
            // Subtle loading indicator showing original is downloading
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 2.5.dp
            )
        } else if (!item.thumbPath.isNullOrEmpty() && File(item.thumbPath).exists()) {
            AsyncImage(
                model = File(item.thumbPath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 2.5.dp
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = Color(0xFFF0AE45),
                strokeWidth = 2.5.dp
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerContent(
    item: MediaItem,
    tdManager: TdClientManager,
    isCurrentPage: Boolean,
    context: Context
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(item.durationSec * 1000L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isFirstFrameRendered by remember { mutableStateOf(false) }

    val minithumbBitmap = remember(item.minithumb) {
        NativeEngine.decodeMinithumb(item.minithumb)
    }

    // Immediately prefetch stream chunk
    LaunchedEffect(item.fileId) {
        tdManager.downloadFileOffset(item.fileId, offset = 0L, limit = 0L, priority = 32)
    }

    // Initialize ExoPlayer with true progressive streaming immediately
    val player = remember(item.fileId) {
        val dataSourceFactory = GrowingFileDataSource.Factory(
            tdManager = tdManager,
            fileId = item.fileId,
            totalSize = item.sizeBytes
        )
        val ext = if (item.fileName?.contains(".") == true) item.fileName.substringAfterLast(".") else "mp4"
        val mediaItem = ExoMediaItem.Builder()
            .setUri(Uri.parse("amar-stream://video/${item.fileId}.$ext"))
            .setMimeType(item.mime ?: "video/mp4")
            .build()

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
            .createMediaSource(mediaItem)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 500, // Starts playing in 500ms
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = isCurrentPage
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = (state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_READY) {
                            isBuffering = false
                        }
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    override fun onRenderedFirstFrame() {
                        isFirstFrameRendered = true
                        isBuffering = false
                    }
                })
            }
    }

    // Update playback progress periodically
    LaunchedEffect(player, isCurrentPage) {
        if (!isCurrentPage) return@LaunchedEffect
        while (true) {
            if (!isSeeking) {
                currentPos = player.currentPosition.coerceAtLeast(0L)
                val d = player.duration
                if (d > 0) duration = d
                isPlaying = player.isPlaying
            }
            delay(250)
        }
    }

    // Pause player when swiped away, resume when returned
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
            player.pause()
            isPlaying = false
        }
    }

    DisposableEffect(item.fileId) {
        onDispose {
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        // Player Surface View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Show Thumbnail placeholder until video's first frame is rendered
        if (!isFirstFrameRendered) {
            val crispThumb = tdManager.getFileLocalPath(item.thumbFileId ?: -1) ?: item.thumbPath
            if (!crispThumb.isNullOrEmpty() && File(crispThumb).exists()) {
                AsyncImage(
                    model = File(crispThumb),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (minithumbBitmap != null) {
                Image(
                    bitmap = minithumbBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Streaming / Buffering Spinner
        if (isBuffering || !isFirstFrameRendered) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                CircularProgressIndicator(color = Color(0xFFF0AE45), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Streaming video...",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center Play / Pause button overlay
        if (showControls && !isBuffering) {
            IconButton(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player.play()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Video Seekbar Overlay — Positioned cleanly ABOVE the bottom action bar (bottom = 76.dp)!
        if (showControls) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dispSec = if (isSeeking) (seekFraction * duration).toLong() / 1000 else currentPos / 1000
                    val totalSec = duration / 1000
                    Text(
                        text = String.format(java.util.Locale.US, "%d:%02d", dispSec / 60, dispSec % 60),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%d:%02d", totalSec / 60, totalSec % 60),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Slider(
                    value = if (isSeeking) seekFraction else if (duration > 0) (currentPos.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { frac ->
                        isSeeking = true
                        seekFraction = frac
                    },
                    onValueChangeFinished = {
                        if (duration > 0) {
                            val targetMs = (seekFraction * duration).toLong()
                            player.seekTo(targetMs)
                        }
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF0AE45),
                        activeTrackColor = Color(0xFFF0AE45),
                        inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                )
            }
        }
    }
}

private fun exportToGallery(context: Context, item: MediaItem, path: String?) {
    val src = path ?: item.localPath
    if (src.isNullOrEmpty() || !File(src).exists()) {
        Toast.makeText(context, "Please wait, downloading original file...", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val srcFile = File(src)
        val resolver = context.contentResolver
        val isVideo = item.kind == MediaKind.VIDEO

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName ?: srcFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime ?: if (isVideo) "video/mp4" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/Amar" else "Pictures/Amar")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(srcFile).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Toast.makeText(context, "Saved original to Gallery!", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareMedia(context: Context, item: MediaItem, path: String?) {
    val src = path ?: item.localPath
    if (src.isNullOrEmpty() || !File(src).exists()) {
        Toast.makeText(context, "Downloading original file to share...", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val file = File(src)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mime ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share media"))
    } catch (e: Exception) {
        Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
