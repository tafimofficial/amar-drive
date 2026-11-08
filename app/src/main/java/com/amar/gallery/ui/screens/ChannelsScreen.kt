package com.amar.gallery.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amar.gallery.data.db.AmarDatabase
import com.amar.gallery.data.model.ChannelItem
import com.amar.gallery.data.tdlib.TdClientManager
import com.amar.gallery.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    db: AmarDatabase,
    tdManager: TdClientManager
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var channels by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newChannelUsername by remember { mutableStateOf("") }

    fun reload() {
        channels = db.getChannels()
    }

    LaunchedEffect(Unit) {
        reload()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Channels & Groups",
                        color = colors.text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg),
                actions = {
                    IconButton(onClick = {
                        isRefreshing = true
                        tdManager.loadChannels {
                            isRefreshing = false
                            reload()
                            Toast.makeText(context, "Channels & groups refreshed", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.text
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        text = "Add Channel / Group",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            )
        },
        containerColor = colors.bg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (channels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "No channels or groups found",
                            color = colors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Channels and groups you manage or join will appear here. You can also add them manually with the '+' button below.",
                            color = colors.muted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                val adminChannels = remember(channels) { channels.filter { it.isAdmin } }
                val memberChannels = remember(channels) { channels.filter { !it.isAdmin } }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (adminChannels.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "My Admin Channels & Groups",
                                count = adminChannels.size
                            )
                        }
                        items(adminChannels, key = { it.chatId }) { channel ->
                            ChannelCard(
                                channel = channel,
                                onToggle = { enabled ->
                                    db.setChannelEnabled(channel.chatId, enabled)
                                    reload()
                                },
                                onDelete = {
                                    db.deleteChannel(channel.chatId)
                                    reload()
                                    Toast.makeText(context, "${channel.title} removed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    if (memberChannels.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            SectionHeader(
                                title = "Joined Channels & Groups",
                                count = memberChannels.size
                            )
                        }
                        items(memberChannels, key = { it.chatId }) { channel ->
                            ChannelCard(
                                channel = channel,
                                onToggle = { enabled ->
                                    db.setChannelEnabled(channel.chatId, enabled)
                                    reload()
                                },
                                onDelete = {
                                    db.deleteChannel(channel.chatId)
                                    reload()
                                    Toast.makeText(context, "${channel.title} removed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = colors.surface,
                title = { Text("Add Channel or Group", color = colors.text, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Enter public username (e.g. @mychannel), Telegram link, or chat ID:",
                            color = colors.muted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newChannelUsername,
                            onValueChange = { newChannelUsername = it },
                            placeholder = { Text("@username, t.me/..., or -100...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val input = newChannelUsername.trim()
                            if (input.isNotBlank()) {
                                showAddDialog = false
                                Toast.makeText(context, "Looking up $input...", Toast.LENGTH_SHORT).show()
                                tdManager.addChatByQuery(input) { success, err ->
                                    if (success) {
                                        reload()
                                        Toast.makeText(context, "Added successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, err ?: "Failed to add", Toast.LENGTH_LONG).show()
                                    }
                                }
                                newChannelUsername = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            )
        }
    }
}

@Composable
fun ChannelCard(
    channel: ChannelItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current

    // Deterministic colorful gradient for avatar based on chat ID
    val gradientColors = remember(channel.chatId) {
        val h1 = kotlin.math.abs(channel.chatId.hashCode() % 360).toFloat()
        listOf(
            Color.hsl(h1, 0.6f, 0.65f),
            Color.hsl((h1 + 40f) % 360f, 0.5f, 0.35f)
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular gradient avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.title.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Channel details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.title,
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (channel.isAdmin) colors.accent.copy(alpha = 0.15f) else colors.surface2)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (channel.isAdmin) "Admin" else "Member",
                            color = if (channel.isAdmin) colors.accent else colors.muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (channel.isGroup) "👥 Group" else "📢 Channel",
                        color = colors.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " · ${if (channel.totalMedia > 0) channel.totalMedia.toString() + " items" else "Synced"}",
                        color = colors.muted,
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Enabled Switch
            Switch(
                checked = channel.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.muted,
                    uncheckedTrackColor = colors.surface2
                )
            )

            // Remove/Delete channel button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove channel",
                    tint = colors.muted.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = colors.text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(colors.surface2)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                color = colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

