package com.janreins.vaultlock.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.janreins.vaultlock.ui.VaultUiState
import com.janreins.vaultlock.ui.VaultViewModel
import com.janreins.vaultlock.ui.theme.Amber400
import com.janreins.vaultlock.ui.theme.Amber500
import com.janreins.vaultlock.ui.theme.EmeraldSuccess
import com.janreins.vaultlock.ui.theme.RedError
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VaultViewModel,
    uiState: VaultUiState,
    onNavigateBack: () -> Unit,
    onWipeApp: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showWipeConfirmationDialog by remember { mutableStateOf(false) }

    // File Picker for importing encrypted backup
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    viewModel.importBackup(bytes) { success, count, message ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                }
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to read file: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Security",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Offline Security Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "100% Offline Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "No internet permissions • Zero cloud sync • Strong AES-256-GCM encryption on device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Security
            SettingsSectionHeader(title = "SECURITY")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Change Master Password
                    SettingsRowItem(
                        icon = Icons.Default.Key,
                        title = "Change Master Password",
                        subtitle = "Re-encrypts all vault entries with new key",
                        onClick = { showChangePasswordDialog = true },
                        testTag = "setting_change_password"
                    )

                    // Biometric Unlock Toggle (if device supports)
                    if (uiState.isBiometricAvailable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Biometric Unlock",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Unlock with fingerprint or face recognition",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.isBiometricEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setBiometricEnabled(enabled, activity) { success, msg ->
                                        if (!success && msg.isNotBlank()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Amber400,
                                    checkedTrackColor = Amber500.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.testTag("setting_biometric_switch")
                            )
                        }
                    }

                    // Auto-Lock Duration
                    val lockLabel = when (uiState.autoLockSeconds) {
                        0L -> "Immediately on background"
                        60L -> "1 minute of inactivity"
                        120L -> "2 minutes of inactivity (Recommended)"
                        300L -> "5 minutes of inactivity"
                        else -> "${uiState.autoLockSeconds / 60} minutes"
                    }
                    SettingsRowItem(
                        icon = Icons.Default.LockClock,
                        title = "Auto-Lock Timer",
                        subtitle = lockLabel,
                        onClick = { showAutoLockDialog = true },
                        testTag = "setting_auto_lock"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Backup & Restore
            SettingsSectionHeader(title = "ENCRYPTED LOCAL BACKUP")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Export Encrypted Backup
                    SettingsRowItem(
                        icon = Icons.Default.Upload,
                        title = "Export Encrypted Backup",
                        subtitle = "Creates an offline AES-256-GCM encrypted file",
                        onClick = {
                            viewModel.exportBackup { backupBytes ->
                                if (backupBytes != null) {
                                    viewModel.suppressNextBackgroundLock()
                                    shareBackupFile(context, backupBytes)
                                }
                            }
                        },
                        testTag = "setting_export_backup"
                    )

                    // Import / Restore Encrypted Backup
                    SettingsRowItem(
                        icon = Icons.Default.Download,
                        title = "Restore Encrypted Backup",
                        subtitle = "Restore passwords from a VaultLock backup file",
                        onClick = {
                            viewModel.suppressNextBackgroundLock()
                            restoreBackupLauncher.launch("*/*")
                        },
                        testTag = "setting_restore_backup"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Appearance
            SettingsSectionHeader(title = "APPEARANCE")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val themeLabel = when (uiState.themeMode) {
                    "dark" -> "Dark Titanium (Default)"
                    "light" -> "Light Clean"
                    else -> "System Default"
                }
                SettingsRowItem(
                    icon = Icons.Default.Brightness4,
                    title = "App Theme",
                    subtitle = themeLabel,
                    onClick = { showThemeDialog = true },
                    testTag = "setting_theme"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Danger Zone
            SettingsSectionHeader(title = "DANGER ZONE", color = RedError)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.08f))
            ) {
                SettingsRowItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Wipe Vault & Reset App",
                    subtitle = "Permanently deletes all saved passwords and cryptographic keys",
                    iconColor = RedError,
                    titleColor = RedError,
                    onClick = { showWipeConfirmationDialog = true },
                    testTag = "setting_wipe_vault"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Change Password Dialog
        if (showChangePasswordDialog) {
            ChangeMasterPasswordDialog(
                onDismiss = { showChangePasswordDialog = false },
                onChange = { currentPass, newPass, confirmPass, onResult ->
                    viewModel.changeMasterPassword(currentPass, newPass, confirmPass, activity) { success, msg ->
                        onResult(success, msg)
                        if (success) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    }
                }
            )
        }

        // Auto Lock Picker Dialog
        if (showAutoLockDialog) {
            AutoLockPickerDialog(
                currentSeconds = uiState.autoLockSeconds,
                onDismiss = { showAutoLockDialog = false },
                onSelect = { seconds ->
                    viewModel.setAutoLockDuration(seconds)
                    showAutoLockDialog = false
                }
            )
        }

        // Theme Picker Dialog
        if (showThemeDialog) {
            ThemePickerDialog(
                currentMode = uiState.themeMode,
                onDismiss = { showThemeDialog = false },
                onSelect = { mode ->
                    viewModel.setTheme(mode)
                    showThemeDialog = false
                }
            )
        }

        // Wipe App Confirmation Dialog
        if (showWipeConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showWipeConfirmationDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = RedError,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = { Text("Erase All Data?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "This will permanently delete your Master Password, biometric tokens, and every encrypted entry in your vault. This action CANNOT be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showWipeConfirmationDialog = false
                            viewModel.wipeAllData(onWipeApp)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedError)
                    ) {
                        Text("Erase Everything", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWipeConfirmationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    color: Color = Amber400
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color = Amber400,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChangeMasterPasswordDialog(
    onDismiss: () -> Unit,
    onChange: (String, String, String, (Boolean, String) -> Unit) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Master Password") },
        text = {
            Column {
                Text(
                    text = "Enter your current password and choose a new one (minimum 8 characters).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMessage = null
                    },
                    label = { Text("Current Password") },
                    visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("New Password") },
                    visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm New Password") },
                    visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = RedError,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onChange(currentPassword, newPassword, confirmPassword) { success, msg ->
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = msg
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Amber500)
            ) {
                Text("Update Password", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AutoLockPickerDialog(
    currentSeconds: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val options = listOf(
        0L to "Immediately when app is backgrounded",
        60L to "1 minute of inactivity",
        120L to "2 minutes of inactivity (Default)",
        300L to "5 minutes of inactivity"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Lock Inactivity Timer") },
        text = {
            Column {
                options.forEach { (seconds, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSeconds == seconds,
                            onClick = { onSelect(seconds) },
                            colors = RadioButtonDefaults.colors(selectedColor = Amber500)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun ThemePickerDialog(
    currentMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        "dark" to "Dark Theme (Recommended)",
        "light" to "Light Theme",
        "system" to "System Default"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Theme") },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = { onSelect(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = Amber500)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun shareBackupFile(context: Context, backupBytes: ByteArray) {
    try {
        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val timestamp = System.currentTimeMillis()
        val file = File(backupDir, "VaultLock_Backup_$timestamp.vault")
        val fos = FileOutputStream(file)
        fos.write(backupBytes)
        fos.flush()
        fos.close()

        val fileUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "VaultLock Encrypted Backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Save or Share Encrypted Backup"))
    } catch (e: Exception) {
        // Fallback to text share if FileProvider is unavailable
        val base64 = android.util.Base64.encodeToString(backupBytes, android.util.Base64.NO_WRAP)
        val textIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, base64)
            putExtra(Intent.EXTRA_SUBJECT, "VaultLock Encrypted Backup (Base64)")
        }
        context.startActivity(Intent.createChooser(textIntent, "Save Encrypted Backup"))
    }
}
