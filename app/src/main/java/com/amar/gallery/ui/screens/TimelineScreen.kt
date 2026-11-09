package com.amar.gallery.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amar.gallery.data.db.AmarDatabase
import com.amar.gallery.data.model.ChannelItem
import com.amar.gallery.data.model.MediaItem
import com.amar.gallery.data.model.MediaKind
import com.amar.gallery.data.tdlib.TdClientManager
import com.amar.gallery.ui.theme.LocalAppColors
import com.amar.gallery.util.NativeEngine
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    db: AmarDatabase,
    tdManager: TdClientManager,
    selectedChannelId: Long,
    onChannelSelected: (Long) -> Unit,
    onMediaClick: (List<MediaItem>, Int) -> Unit
) {
    val colors = LocalAppColors.current
    var selectedFilter by remember { mutableStateOf(MediaKind.ALL) }
    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var channels by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var channelDropdownExpanded by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<Long>() }

    val syncMsg by tdManager.syncProgress.collectAsState()

    // Reload channels and media
    fun refreshData() {
        channels = db.getChannels()
        mediaList = db.getTimelineMedia(selectedChannelId, selectedFilter, limit = 5000)
    }

    LaunchedEffect(selectedChannelId, selectedFilter) {
        refreshData()
    }

    LaunchedEffect(syncMsg) {
        refreshData()
    }

    val currentChannelTitle = remember(selectedChannelId, channels) {
        if (selectedChannelId == 0L) "All Channels & Groups"
        else {
            val ch = channels.find { it.chatId == selectedChannelId }
            if (ch != null) "${if (ch.isGroup) "👥 " else "📢 "}${ch.title}" else "Timeline"
        }
    }

    // Group media items by date bucket for sticky date headers
    val groupedItems = remember(mediaList) {
        mediaList.groupBy { NativeEngine.getDateHeader(it.date) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column(
                    modifier = Modifier.clickable { channelDropdownExpanded = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentChannelTitle,
                            color = colors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Switch channel",
                            tint = colors.muted
                        )
                    }
                    Text(
                        text = "${mediaList.size} files & media in total",
                        color = colors.muted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                DropdownMenu(
                    expanded = channelDropdownExpanded,
                    onDismissRequest = { channelDropdownExpanded = false },
                    modifier = Modifier.background(colors.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Channels & Groups", color = colors.text, fontWeight = if (selectedChannelId == 0L) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onChannelSelected(0L)
                            channelDropdownExpanded = false
                        }
                    )
                    channels.forEach { ch ->
                        DropdownMenuItem(
                            text = { Text("${if (ch.isGroup) "👥 " else "📢 "}${ch.title}", color = colors.text, fontWeight = if (selectedChannelId == ch.chatId) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onChannelSelected(ch.chatId)
                                channelDropdownExpanded = false
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.bg
            ),
            actions = {
                if (isSelectionMode) {
                    IconButton(onClick = {
                        isSelectionMode = false
                        selectedItems.clear()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close selection", tint = colors.text)
                    }
                } else {
                    IconButton(onClick = { tdManager.loadChannels { refreshData() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = colors.text)
                    }
                }
            }
        )

        // Sync banner if active
        if (syncMsg.isNotEmpty() && !syncMsg.contains("Synced")) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.accent,
                trackColor = colors.surface2
            )
        }

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                MediaKind.ALL to "All",
                MediaKind.PHOTO to "Photos",
                MediaKind.VIDEO to "Videos",
                MediaKind.FILE to "Files"
            )

            filters.forEach { (kind, label) ->
                val isSelected = selectedFilter == kind
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .clickable { selectedFilter = kind },
                    color = if (isSelected) colors.accent else colors.surface2,
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) colors.onAccent else colors.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Timeline Grid
        Box(modifier = Modifier.weight(1f)) {
            if (mediaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No media found in channel",
                            color = colors.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "New uploads will appear automatically",
                            color = colors.muted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    groupedItems.forEach { (dateHeader, itemsInGroup) ->
                        // Header
                        item(span = { GridItemSpan(3) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateHeader,
                                    color = colors.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${itemsInGroup.size} items",
                                    color = colors.muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Media Items
                        items(itemsInGroup, key = { it.stableId }) { item ->
                            MediaGridCell(
                                item = item,
                                tdManager = tdManager,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedItems.contains(item.stableId),
                                onLongClick = {
                                    isSelectionMode = true
                                    if (!selectedItems.contains(item.stableId)) {
                                        selectedItems.add(item.stableId)
                                    }
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        if (selectedItems.contains(item.stableId)) {
                                            selectedItems.remove(item.stableId)
                                            if (selectedItems.isEmpty()) isSelectionMode = false
                                        } else {
                                            selectedItems.add(item.stableId)
                                        }
                                    } else {
                                        val idx = mediaList.indexOf(item)
                                        onMediaClick(mediaList, if (idx >= 0) idx else 0)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaGridCell(
    item: MediaItem,
    tdManager: TdClientManager,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    // Trigger download of medium thumbnail if not already present
    LaunchedEffect(item.thumbFileId) {
        val fid = item.thumbFileId
        if (fid != null && fid > 0) {
            val p = tdManager.getFileLocalPath(fid)
            if (p.isNullOrEmpty() || !File(p).exists()) {
                tdManager.downloadFile(fid, priority = 2)
            }
        }
    }

    var liveThumbPath by remember(item.thumbFileId) {
        mutableStateOf(item.thumbFileId?.let { tdManager.getFileLocalPath(it) } ?: item.thumbPath)
    }
    var liveLocalPath by remember(item.fileId) {
        mutableStateOf(tdManager.getFileLocalPath(item.fileId) ?: item.localPath)
    }

    LaunchedEffect(item.thumbFileId, item.fileId) {
        tdManager.fileEvents.collect { fileId ->
            if (fileId == item.thumbFileId) {
                val p = tdManager.getFileLocalPath(fileId)
                if (!p.isNullOrEmpty()) liveThumbPath = p
            } else if (fileId == item.fileId) {
                val p = tdManager.getFileLocalPath(fileId)
                if (!p.isNullOrEmpty()) liveLocalPath = p
            }
        }
    }

    val minithumbBitmap = remember(item.minithumb) {
        NativeEngine.decodeMinithumb(item.minithumb)
    }

    val crispFile = when {
        !liveLocalPath.isNullOrEmpty() && File(liveLocalPath!!).exists() -> File(liveLocalPath!!)
        !liveThumbPath.isNullOrEmpty() && File(liveThumbPath!!).exists() -> File(liveThumbPath!!)
        else -> null
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(colors.surface2)
            .clickable(onClick = onClick)
    ) {
        if (crispFile != null) {
            // High/Medium Quality Crisp Image!
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(crispFile)
                    .crossfade(true)
                    .build(),
                contentDescription = item.caption ?: item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (minithumbBitmap != null) {
            // Temporary instant blur placeholder while medium thumbnail is loading
            Image(
                bitmap = minithumbBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.kind == MediaKind.VIDEO) Icons.Default.PlayCircle else Icons.Default.Image,
                    contentDescription = null,
                    tint = colors.muted.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // 3. Video indicator & duration pill
        if (item.kind == MediaKind.VIDEO) {
            // Play triangle icon in top-left
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Duration pill in bottom-right
            if (item.durationSec > 0) {
                Box(
                    modifier = Modifier
                        .padding(5.dp)
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.formattedDuration,
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Selection overlay
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) colors.accent.copy(alpha = 0.35f) else Color.Transparent)
            )
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.accent else colors.surface.copy(alpha = 0.8f))
                    .border(1.5.dp, if (isSelected) colors.accent else Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = colors.onAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

