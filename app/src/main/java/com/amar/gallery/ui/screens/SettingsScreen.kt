package com.amar.gallery.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amar.gallery.data.pref.AppPreferences
import com.amar.gallery.data.tdlib.TdClientManager
import com.amar.gallery.ui.theme.LocalAppColors
import com.amar.gallery.util.NativeEngine
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: AppPreferences,
    tdManager: TdClientManager,
    onLogout: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var appLock by remember { mutableStateOf(prefs.appLockEnabled) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnlyEnabled) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Calculate cache size
    val cacheBytes = remember {
        fun dirSize(dir: File): Long {
            if (!dir.exists()) return 0L
            var size = 0L
            dir.listFiles()?.forEach { f ->
                size += if (f.isDirectory) dirSize(f) else f.length()
            }
            return size
        }
        val tdFiles = File(context.filesDir, "td/files")
        val cacheDir = context.cacheDir
        dirSize(tdFiles) + dirSize(cacheDir)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        color = colors.text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        containerColor = colors.bg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account Card
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
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prefs.userName.ifEmpty { "Telegram User" },
                            color = colors.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = prefs.userPhone,
                            color = colors.muted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Storage & Cache Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = colors.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Storage & Hybrid Cache",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Used ${NativeEngine.formatBytes(cacheBytes)} of ${prefs.cacheLimitMb / 1024} GB cap",
                        color = colors.muted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = {
                            val max = prefs.cacheLimitMb * 1024L * 1024L
                            (cacheBytes.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = colors.accent,
                        trackColor = colors.surface2
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val tdFiles = File(context.filesDir, "td/files")
                            tdFiles.deleteRecursively()
                            context.cacheDir.deleteRecursively()
                            Toast.makeText(context, "Cache cleaned successfully", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surface2,
                            contentColor = colors.text
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Cache", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Theme Preferences
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = colors.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Theme",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM" to "System", "DARK" to "Dark", "LIGHT" to "Light").forEach { (mode, label) ->
                            val isSelected = themeMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        themeMode = mode
                                        prefs.themeMode = mode
                                    },
                                color = if (isSelected) colors.accent else colors.surface2,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) colors.onAccent else colors.muted,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // App Lock & Wi-Fi Toggles
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = colors.surface
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Biometric Lock
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Lock (Biometric)",
                                color = colors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Require fingerprint / face to unlock",
                                color = colors.muted,
                                fontSize = 12.5.sp
                            )
                        }
                        Switch(
                            checked = appLock,
                            onCheckedChange = {
                                appLock = it
                                prefs.appLockEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccent,
                                checkedTrackColor = colors.accent
                            )
                        )
                    }

                    HorizontalDivider(color = colors.line, thickness = 0.5.dp)

                    // Wi-Fi Only
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wi-Fi Only Downloads",
                                color = colors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Save mobile data for full media",
                                color = colors.muted,
                                fontSize = 12.5.sp
                            )
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = {
                                wifiOnly = it
                                prefs.wifiOnlyEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccent,
                                checkedTrackColor = colors.accent
                            )
                        )
                    }
                }
            }

            // Native Engine Diagnostics
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = colors.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Native Engine Core",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (NativeEngine.isLoaded()) NativeEngine.initEngine() else "C Core Loading...",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Logout Button
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F).copy(alpha = 0.15f),
                    contentColor = Color(0xFFEF5350)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Log Out",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                containerColor = colors.surface,
                title = { Text("Log Out?", color = colors.text, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to log out? Local caches and index will be cleaned.",
                        color = colors.muted,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            tdManager.logOut {
                                onLogout()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Log Out")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            )
        }
    }
}

