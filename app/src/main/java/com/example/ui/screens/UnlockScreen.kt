package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.crypto.BiometricHelper
import com.example.generator.PasswordGenerator
import com.example.ui.VaultUiState
import com.example.ui.VaultViewModel
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RedError

@Composable
fun UnlockScreen(
    viewModel: VaultViewModel,
    uiState: VaultUiState,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var enableBiometricInSetup by remember { mutableStateOf(true) }

    // Auto-trigger biometric on start if returning user with biometric enabled
    LaunchedEffect(uiState.isSetup, uiState.isBiometricEnabled) {
        if (uiState.isSetup && uiState.isBiometricEnabled && !uiState.isUnlocked) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                BiometricHelper.promptBiometric(
                    activity = activity,
                    onSuccess = {
                        viewModel.unlockWithBiometric(onUnlocked)
                    },
                    onError = { errorMsg ->
                        if (errorMsg != "cancelled") {
                            // show feedback or keep manual unlock active
                        }
                    }
                )
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Vault Icon Shield Motif
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Amber500.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isSetup) Icons.Default.Lock else Icons.Default.Shield,
                            contentDescription = "Vault Security Logo",
                            tint = Amber400,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "VaultLock",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (uiState.isSetup) "Enter your Master Password to unlock" else "Create your Master Password to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Setup Card or Unlock Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                viewModel.clearUnlockError()
                            },
                            label = { Text(if (uiState.isSetup) "Master Password" else "New Master Password") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Amber400)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (uiState.isSetup) ImeAction.Done else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (uiState.isSetup) {
                                        viewModel.unlockWithPassword(password, onUnlocked)
                                    }
                                }
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("master_password_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Amber400,
                                cursorColor = Amber400
                            )
                        )

                        // If in First-time Setup: Show strength & confirm password field
                        if (!uiState.isSetup) {
                            Spacer(modifier = Modifier.height(12.dp))

                            if (password.isNotEmpty()) {
                                val strength = PasswordGenerator.evaluateStrength(password)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Strength: ${strength.label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(strength.colorHex),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (password.length >= 8) "${password.length} chars" else "Min 8 chars",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (password.length >= 8) EmeraldSuccess else RedError
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { strength.score },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = Color(strength.colorHex),
                                        trackColor = MaterialTheme.colorScheme.surface
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    viewModel.clearUnlockError()
                                },
                                label = { Text("Confirm Master Password") },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Amber400)
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        viewModel.setupMasterPassword(
                                            password = password,
                                            confirm = confirmPassword,
                                            enableBiometric = enableBiometricInSetup && uiState.isBiometricAvailable,
                                            onSuccess = onUnlocked
                                        )
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_password_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber400,
                                    cursorColor = Amber400
                                )
                            )

                            if (uiState.isBiometricAvailable) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = enableBiometricInSetup,
                                        onCheckedChange = { enableBiometricInSetup = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Amber500),
                                        modifier = Modifier.testTag("enable_biometric_checkbox")
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Enable Biometric Unlock (Fingerprint / Face)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Error message
                        AnimatedVisibility(
                            visible = uiState.unlockError != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            if (uiState.unlockError != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = RedError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = uiState.unlockError,
                                        color = RedError,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Unlock / Setup Action Button
                        Button(
                            onClick = {
                                if (uiState.isSetup) {
                                    viewModel.unlockWithPassword(password, onUnlocked)
                                } else {
                                    viewModel.setupMasterPassword(
                                        password = password,
                                        confirm = confirmPassword,
                                        enableBiometric = enableBiometricInSetup && uiState.isBiometricAvailable,
                                        onSuccess = onUnlocked
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("unlock_submit_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Amber500,
                                contentColor = Color(0xFF0F172A)
                            )
                        ) {
                            Icon(
                                imageVector = if (uiState.isSetup) Icons.Default.LockOpen else Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isSetup) "Unlock Vault" else "Initialize Secure Vault",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // Biometric Quick Button (if returning user)
                        if (uiState.isSetup && uiState.isBiometricEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    val activity = context as? FragmentActivity
                                    if (activity != null) {
                                        BiometricHelper.promptBiometric(
                                            activity = activity,
                                            onSuccess = {
                                                viewModel.unlockWithBiometric(onUnlocked)
                                            },
                                            onError = {
                                                // Keep screen active
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("biometric_unlock_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = "Biometric unlock icon",
                                    tint = Amber400,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Unlock with Biometrics",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Security Badge Note
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "100% Offline • Zero Network Traffic • AES-256-GCM Encryption. Your Master Password never leaves this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
