package com.amar.gallery.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amar.gallery.data.model.AuthState
import com.amar.gallery.data.tdlib.TdClientManager
import com.amar.gallery.ui.theme.LocalAppColors

@Composable
fun LoginScreen(
    tdManager: TdClientManager,
    onLoginSuccess: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val authState by tdManager.authState.collectAsState()

    var phoneNumber by remember { mutableStateOf("+8801786925863") }
    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showCodeDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrLink by remember { mutableStateOf("") }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.WaitPhone -> {
                isLoading = false
            }
            is AuthState.WaitQr -> {
                isLoading = false
                qrLink = state.link
                showQrDialog = true
            }
            is AuthState.WaitCode -> {
                isLoading = false
                codeError = null
                showCodeDialog = true
            }
            is AuthState.WaitPassword -> {
                isLoading = false
                passwordError = null
                showCodeDialog = false
                showPasswordDialog = true
            }
            is AuthState.Ready -> {
                isLoading = false
                showCodeDialog = false
                showPasswordDialog = false
                Toast.makeText(context, "Welcome ${state.userFirstName}!", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
            }
            is AuthState.Error -> {
                isLoading = false
                errorMessage = state.message
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Amber Gradient Logo Badge
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF0AE45), Color(0xFFC9712A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Headline
            Text(
                text = "Your channel,\nas a gallery",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                lineHeight = 38.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Subhead
            Text(
                text = "Sign in with your Telegram account to browse the photos and videos of channels you manage.",
                fontSize = 14.sp,
                color = colors.muted,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Phone Input Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = colors.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("+8801786925863", color = colors.muted) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFFE53935),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Send Code Button
            Button(
                onClick = {
                    if (phoneNumber.isNotBlank()) {
                        isLoading = true
                        errorMessage = null
                        tdManager.sendPhoneNumber(phoneNumber) { success, err ->
                            isLoading = false
                            if (!success) {
                                errorMessage = err ?: "Failed to send code"
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent
                ),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colors.onAccent,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Send code",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Contextual resume buttons if user is awaiting code or password
            if (authState is AuthState.WaitCode) {
                OutlinedButton(
                    onClick = { showCodeDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = "Enter Verification Code (OTP)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (authState is AuthState.WaitPassword) {
                OutlinedButton(
                    onClick = { showPasswordDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = "Enter 2FA Cloud Password",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    tdManager.requestQrCode()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "Log in with Telegram App / QR",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Read-only note
            Text(
                text = "Read-only access. Your session is encrypted and stays on this device.",
                fontSize = 12.sp,
                color = colors.muted,
                lineHeight = 18.sp
            )
        }

        // Verification Code Dialog
        if (showCodeDialog) {
            AlertDialog(
                onDismissRequest = { showCodeDialog = false },
                containerColor = colors.surface,
                title = {
                    Text(
                        text = "Enter Verification Code",
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Telegram sent a verification code for $phoneNumber. Check your Telegram app or SMS.",
                            color = colors.muted,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = {
                                codeInput = it.filter { ch -> ch.isDigit() }
                                codeError = null
                            },
                            label = { Text("5-Digit Code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = codeError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        if (codeError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = codeError ?: "",
                                color = Color(0xFFE53935),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (codeInput.isNotBlank()) {
                                isLoading = true
                                codeError = null
                                tdManager.sendCode(codeInput) { success, err ->
                                    isLoading = false
                                    if (!success) {
                                        codeError = err ?: "Invalid code. Please try again."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        )
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCodeDialog = false }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            )
        }

        // 2FA Password Dialog
        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                containerColor = colors.surface,
                title = {
                    Text(
                        text = "Two-Step Verification (2FA)",
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Your Telegram account is protected with Two-Step Verification. Enter your Cloud Password.",
                            color = colors.muted,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                passwordError = null
                            },
                            label = { Text("2FA Cloud Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = passwordError != null,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                        tint = colors.muted
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        if (passwordError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = passwordError ?: "",
                                color = Color(0xFFE53935),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (passwordInput.isNotBlank()) {
                                isLoading = true
                                passwordError = null
                                tdManager.sendPassword(passwordInput) { success, err ->
                                    isLoading = false
                                    if (!success) {
                                        passwordError = err ?: "Incorrect password. Please try again."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        )
                    ) {
                        Text("Submit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            )
        }

        // QR / Instant Device Link Dialog
        if (showQrDialog) {
            AlertDialog(
                onDismissRequest = { showQrDialog = false },
                containerColor = colors.surface,
                title = {
                    Text(
                        text = "Telegram Quick Link",
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (qrLink.isNotEmpty()) {
                            AsyncImage(
                                model = "https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=" + java.net.URLEncoder.encode(qrLink, "UTF-8"),
                                contentDescription = "Scan QR Code",
                                modifier = Modifier
                                    .size(190.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(8.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        Text(
                            text = "Scan this QR code from Telegram Settings -> Devices -> Link Desktop Device, or tap 'Open in Telegram'.",
                            color = colors.muted,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qrLink))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Telegram: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent
                            )
                        ) {
                            Text("Open in Telegram", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showQrDialog = false }) {
                        Text("Close", color = colors.muted)
                    }
                }
            )
        }
    }
}

