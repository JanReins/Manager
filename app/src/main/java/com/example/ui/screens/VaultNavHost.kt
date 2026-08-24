package com.example.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.VaultViewModel

object VaultDestinations {
    const val UNLOCK = "unlock"
    const val VAULT = "vault"
    const val ADD_EDIT = "add_edit/{entryId}"
    const val GENERATOR = "generator"
    const val SETTINGS = "settings"

    fun addEditRoute(entryId: Long = 0L) = "add_edit/$entryId"
}

@Composable
fun VaultNavHost(
    navController: NavHostController,
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val entries by viewModel.entries.collectAsState()

    // Route guard: if vault is locked or master password is reset, navigate to unlock
    LaunchedEffect(uiState.isUnlocked) {
        if (!uiState.isUnlocked) {
            navController.navigate(VaultDestinations.UNLOCK) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (uiState.isUnlocked) VaultDestinations.VAULT else VaultDestinations.UNLOCK,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(220)) }
    ) {
        composable(VaultDestinations.UNLOCK) {
            UnlockScreen(
                viewModel = viewModel,
                uiState = uiState,
                onUnlocked = {
                    navController.navigate(VaultDestinations.VAULT) {
                        popUpTo(VaultDestinations.UNLOCK) { inclusive = true }
                    }
                }
            )
        }

        composable(VaultDestinations.VAULT) {
            VaultScreen(
                viewModel = viewModel,
                uiState = uiState,
                entries = entries,
                onAddEntry = {
                    navController.navigate(VaultDestinations.addEditRoute(0L))
                },
                onEditEntry = { id ->
                    navController.navigate(VaultDestinations.addEditRoute(id))
                },
                onOpenGenerator = {
                    navController.navigate(VaultDestinations.GENERATOR)
                },
                onOpenSettings = {
                    navController.navigate(VaultDestinations.SETTINGS)
                },
                onLock = {
                    viewModel.lockVault()
                }
            )
        }

        composable(
            route = VaultDestinations.ADD_EDIT,
            arguments = listOf(
                navArgument("entryId") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: 0L
            AddEditEntryScreen(
                entryId = entryId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(VaultDestinations.GENERATOR) {
            PasswordGeneratorScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(VaultDestinations.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                uiState = uiState,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onWipeApp = {
                    navController.navigate(VaultDestinations.UNLOCK) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
