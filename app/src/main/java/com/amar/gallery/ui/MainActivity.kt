package com.amar.gallery.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.amar.gallery.data.db.AmarDatabase
import com.amar.gallery.data.model.MediaItem
import com.amar.gallery.data.pref.AppPreferences
import com.amar.gallery.data.tdlib.TdClientManager
import com.amar.gallery.ui.screens.*
import com.amar.gallery.ui.theme.AmarTheme
import com.amar.gallery.ui.theme.LocalAppColors

enum class BottomTab(val title: String, val icon: ImageVector) {
    PHOTOS("Photos", Icons.Default.PhotoLibrary),
    CHANNELS("Channels", Icons.Default.Campaign),
    FILES("Files", Icons.Default.Folder),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : FragmentActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var db: AmarDatabase
    private lateinit var tdManager: TdClientManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = AppPreferences.getInstance(this)
        db = AmarDatabase.getInstance(this)
        tdManager = TdClientManager.getInstance(this)
        tdManager.initialize()

        // Check Biometric App Lock
        var isLocked = prefs.appLockEnabled

        setContent {
            var lockedState by remember { mutableStateOf(isLocked) }
            var isLoggedIn by remember { mutableStateOf(prefs.isLoggedIn) }
            var currentTab by remember { mutableStateOf(BottomTab.PHOTOS) }
            var selectedChannelId by remember { mutableStateOf(prefs.selectedChannelId) }
            var viewingMediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
            var viewingMediaIndex by remember { mutableStateOf<Int?>(null) }

            var lastBackPressTime by remember { mutableStateOf(0L) }

            // Handle back button presses gracefully:
            // 1. If viewer is open, back closes the viewer
            // 2. If on any other tab, back returns to Photos tab
            // 3. If on root Photos tab, requires double-press within 2s to exit so app never closes abruptly
            BackHandler(enabled = true) {
                if (viewingMediaIndex != null) {
                    viewingMediaIndex = null
                    viewingMediaList = emptyList()
                } else if (currentTab != BottomTab.PHOTOS) {
                    currentTab = BottomTab.PHOTOS
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressTime < 2000L) {
                        finish()
                    } else {
                        lastBackPressTime = now
                        Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            AmarTheme(themePreference = prefs.themeMode) {
                val colors = LocalAppColors.current

                if (lockedState) {
                    // Biometric Unlock Overlay
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = colors.bg
                    ) {
                        LaunchedEffect(Unit) {
                            showBiometricPrompt { success ->
                                if (success) lockedState = false
                            }
                        }
                    }
                } else if (!isLoggedIn) {
                    LoginScreen(
                        tdManager = tdManager,
                        onLoginSuccess = {
                            isLoggedIn = true
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = colors.surface,
                                    contentColor = colors.text,
                                    tonalElevation = 0.dp
                                ) {
                                    BottomTab.values().forEach { tab ->
                                        val isSelected = currentTab == tab
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.title
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = tab.title,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = colors.accent,
                                                selectedTextColor = colors.accent,
                                                unselectedIconColor = colors.muted,
                                                unselectedTextColor = colors.muted,
                                                indicatorColor = colors.surface2
                                            )
                                        )
                                    }
                                }
                            },
                            containerColor = colors.bg
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (currentTab) {
                                    BottomTab.PHOTOS -> TimelineScreen(
                                        db = db,
                                        tdManager = tdManager,
                                        selectedChannelId = selectedChannelId,
                                        onChannelSelected = { id ->
                                            selectedChannelId = id
                                            prefs.selectedChannelId = id
                                        },
                                        onMediaClick = { list, idx ->
                                            viewingMediaList = list
                                            viewingMediaIndex = idx
                                        }
                                    )
                                    BottomTab.CHANNELS -> ChannelsScreen(
                                        db = db,
                                        tdManager = tdManager
                                    )
                                    BottomTab.FILES -> FilesScreen(
                                        db = db,
                                        tdManager = tdManager,
                                        selectedChannelId = selectedChannelId
                                    )
                                    BottomTab.SETTINGS -> SettingsScreen(
                                        prefs = prefs,
                                        tdManager = tdManager,
                                        onLogout = {
                                            isLoggedIn = false
                                        }
                                    )
                                }
                            }
                        }

                        // Full-screen Viewer Overlay with swipe between photos and videos
                        viewingMediaIndex?.let { index ->
                            ViewerScreen(
                                mediaList = viewingMediaList,
                                initialIndex = index,
                                db = db,
                                tdManager = tdManager,
                                onClose = {
                                    viewingMediaIndex = null
                                    viewingMediaList = emptyList()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt(onResult: (Boolean) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(this@MainActivity, "Unlock required: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(this@MainActivity, "Biometric failed", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Amar")
            .setSubtitle("Authenticate using your fingerprint or screen lock")
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }
}

