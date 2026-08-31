package com.janreins.vaultlock.ui.screens

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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.janreins.vaultlock.ui.VaultUiState
import com.janreins.vaultlock.ui.VaultViewModel
import com.janreins.vaultlock.ui.theme.Amber400
import com.janreins.vaultlock.ui.theme.Amber500
import com.janreins.vaultlock.ui.theme.EmeraldSuccess
import com.janreins.vaultlock.ui.theme.RedError

@Composable
fun UnlockScreen(
    viewModel: VaultViewModel,
    uiState: VaultUiState
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var enableBiometricCheckbox by remember { mutableStateOf(true) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Proactively trigger biometric prompt if available and enabled
    LaunchedEffect(uiState.isMasterPasswordSet, uiState.isBiometricEnabled) {
        if (uiState.isMasterPasswordSet && uiState.isBiometricEnabled && activity != null) {
            viewModel.unlockWithBiometric(activity) { success, msg ->
                if (!success && msg.isNotBlank()) {
                    errorMessage = msg
                }
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
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo Icon
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "VaultLock Security",
                        tint = Amber400,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "VaultLock",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Amber400
                )

                Text(
                    text = if (uiState.isMasterPasswordSet) "Private • Offline • AES-256 Encrypted" else "Create Master Password to Secure Vault",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!uiState.isMasterPasswordSet) {
                            // SETUP MODE (First Launch)
                            Text(
                                text = "Setup Master Key",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "This single password encrypts and protects all your credentials. It is never stored in plaintext and cannot be recovered if lost.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    errorMessage = null
                                },
                                label = { Text("Master Password (min 8 chars)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, null, tint = Amber400)
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { isPasswordVisible = !isPasswordVisible },
                                        modifier = Modifier.testTag("unlock_visibility_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber400,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_password_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = confirmPasswordInput,
                                onValueChange = {
                                    confirmPasswordInput = it
                                    errorMessage = null
                                },
                                label = { Text("Confirm Master Password") },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckCircle, null, tint = Amber400)
                                },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        // Execute setup
                                        if (passwordInput.length < 8) {
                                            errorMessage = "Password must be at least 8 characters long"
                                        } else if (passwordInput != confirmPasswordInput) {
                                            errorMessage = "Passwords do not match"
                                        } else {
                                            isProcessing = true
                                            viewModel.setupMasterPassword(passwordInput, enableBiometricCheckbox, activity) { success, msg ->
                                                isProcessing = false
                                                if (!success) errorMessage = msg
                                            }
                                        }
                                    }
                                ),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber400,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_confirm_password_input")
                            )

                            if (uiState.isBiometricAvailable) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = enableBiometricCheckbox,
                                        onCheckedChange = { enableBiometricCheckbox = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Amber500,
                                            checkmarkColor = Color(0xFF0F172A)
                                        ),
                                        modifier = Modifier.testTag("setup_biometric_checkbox")
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Enable Biometric Unlock (Fingerprint / Face)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (passwordInput.length < 8) {
                                        errorMessage = "Password must be at least 8 characters long"
                                    } else if (passwordInput != confirmPasswordInput) {
                                        errorMessage = "Passwords do not match"
                                    } else {
                                        isProcessing = true
                                        viewModel.setupMasterPassword(passwordInput, enableBiometricCheckbox, activity) { success, msg ->
                                            isProcessing = false
                                            if (!success) errorMessage = msg
                                        }
                                    }
                                },
                                enabled = !isProcessing && passwordInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("setup_create_vault_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Amber500)
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color(0xFF0F172A),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Create Secure Vault",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                        } else {
                            // UNLOCK MODE
                            Text(
                                text = "Enter Master Password",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    errorMessage = null
                                },
                                label = { Text("Master Password") },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, null, tint = Amber400)
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { isPasswordVisible = !isPasswordVisible },
                                        modifier = Modifier.testTag("unlock_visibility_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (passwordInput.isNotBlank()) {
                                            isProcessing = true
                                            viewModel.unlockWithPassword(passwordInput) { success, msg ->
                                                isProcessing = false
                                                if (!success) errorMessage = msg
                                            }
                                        }
                                    }
                                ),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber400,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("unlock_password_input")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (passwordInput.isNotBlank()) {
                                        isProcessing = true
                                        viewModel.unlockWithPassword(passwordInput) { success, msg ->
                                            isProcessing = false
                                            if (!success) errorMessage = msg
                                        }
                                    }
                                },
                                enabled = !isProcessing && passwordInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("unlock_vault_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Amber500)
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color(0xFF0F172A),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Unlock Vault",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }

                            if (uiState.isBiometricEnabled && uiState.isBiometricAvailable && activity != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.unlockWithBiometric(activity) { success, msg ->
                                            if (!success && msg.isNotBlank()) {
                                                errorMessage = msg
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("unlock_biometric_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = Amber400
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Unlock with Biometrics", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // Error Message Display
                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = RedError,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Security guarantees footer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% Offline • Zero Telemetry • On-Device Only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
