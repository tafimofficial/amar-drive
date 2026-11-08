package com.amar.gallery.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.amar.gallery.data.db.AmarDatabase
import com.amar.gallery.data.model.MediaItem
import com.amar.gallery.data.tdlib.TdClientManager
import com.amar.gallery.ui.theme.LocalAppColors
import com.amar.gallery.util.NativeEngine
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    db: AmarDatabase,
    tdManager: TdClientManager,
    selectedChannelId: Long
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var filesList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    fun reload() {
        filesList = db.getFilesMedia(selectedChannelId, limit = 200)
    }

    LaunchedEffect(selectedChannelId) {
        reload()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Files & Documents",
                    color = colors.text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
        )

        if (filesList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No files found",
                        color = colors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PDFs, APKs, Audio & Archives appear here",
                        color = colors.muted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filesList, key = { it.stableId }) { fileItem ->
                    FileRowItem(
                        item = fileItem,
                        onOpen = {
                            if (!fileItem.localPath.isNullOrEmpty() && File(fileItem.localPath).exists()) {
                                try {
                                    val f = File(fileItem.localPath)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        f
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, fileItem.mime ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open file with"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Downloading file...", Toast.LENGTH_SHORT).show()
                                tdManager.downloadFile(fileItem.fileId, priority = 32)
                            }
                        },
                        onShare = {
                            if (!fileItem.localPath.isNullOrEmpty() && File(fileItem.localPath).exists()) {
                                val f = File(fileItem.localPath)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    f
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = fileItem.mime ?: "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share file"))
                            } else {
                                Toast.makeText(context, "Downloading first to share...", Toast.LENGTH_SHORT).show()
                                tdManager.downloadFile(fileItem.fileId, priority = 32)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FileRowItem(
    item: MediaItem,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val colors = LocalAppColors.current

    val extension = remember(item.fileName) {
        val name = item.fileName.orEmpty()
        val dot = name.lastIndexOf('.')
        if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1).uppercase() else "FILE"
    }

    val badgeColor = remember(extension) {
        when (extension) {
            "PDF" -> Color(0xFFE53935)
            "APK" -> Color(0xFF43A047)
            "ZIP", "RAR", "7Z" -> Color(0xFFFB8C00)
            "MP3", "M4A", "WAV", "OGG" -> Color(0xFF8E24AA)
            else -> Color(0xFF1E88E5)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Extension Pill Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = extension.take(4),
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName ?: "Document",
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${NativeEngine.formatBytes(item.sizeBytes)} · ${NativeEngine.getDateHeader(item.date)}",
                    color = colors.muted,
                    fontSize = 12.5.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action: Share
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

