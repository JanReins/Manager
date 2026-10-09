package com.janreins.vaultlock.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.janreins.vaultlock.ui.VaultViewModel

sealed class Screen(val route: String) {
    data object Vault : Screen("vault")
    data object AddEntry : Screen("add_entry")
    data object EditEntry : Screen("edit_entry/{entryId}") {
        fun createRoute(entryId: Long) = "edit_entry/$entryId"
    }
    data object Generator : Screen("generator")
    data object Settings : Screen("settings")
}

@Composable
fun VaultNavHost(
    viewModel: VaultViewModel,
    onWipeApp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeUserMessage()
        }
    }

    // Register above the lock branch so results survive lock and activity recreation.
    val saveBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> viewModel.completeExport(uri) }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> viewModel.onImportFilePicked(uri) }

    LaunchedEffect(uiState.exportReadyFileName) {
        uiState.exportReadyFileName?.let { fileName ->
            viewModel.consumeExportReadyFileName()
            viewModel.suppressNextBackgroundLock()
            try {
                saveBackupLauncher.launch(fileName)
            } catch (_: Exception) {
                viewModel.onBackupPickerLaunchFailed(export = true)
            }
        }
    }

    if (uiState.isUnlocked && uiState.hasPendingImport) {
        BackupPasswordDialog(
            export = false,
            onDismiss = viewModel::discardPendingImport,
            onConfirm = { password, legacy, skipDuplicates ->
                viewModel.confirmPendingImport(password.toCharArray(), legacy, skipDuplicates)
            }
        )
    }

    if (!uiState.isUnlocked) {
        UnlockScreen(viewModel = viewModel, uiState = uiState)
    } else {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Screen.Vault.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable(Screen.Vault.route) {
                VaultScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateToAdd = { navController.navigate(Screen.AddEntry.route) },
                    onNavigateToEdit = { entryId ->
                        navController.navigate(Screen.EditEntry.createRoute(entryId))
                    },
                    onNavigateToGenerator = { navController.navigate(Screen.Generator.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.AddEntry.route) { backStackEntry ->
                AddEditEntryScreen(
                    entryId = null,
                    viewModel = viewModel,
                    draftKey = backStackEntry.id,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.EditEntry.route,
                arguments = listOf(navArgument("entryId") { type = NavType.LongType })
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getLong("entryId")
                AddEditEntryScreen(
                    entryId = entryId,
                    viewModel = viewModel,
                    draftKey = backStackEntry.id,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Generator.route) {
                PasswordGeneratorScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                    onWipeApp = onWipeApp,
                    onRestoreBackup = {
                        if (viewModel.beginImportPicker()) {
                            viewModel.suppressNextBackgroundLock()
                            try {
                                restoreBackupLauncher.launch("*/*")
                            } catch (_: Exception) {
                                viewModel.onBackupPickerLaunchFailed(export = false)
                            }
                        }
                    }
                )
            }
        }
    }
}
