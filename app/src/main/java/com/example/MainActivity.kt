package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.example.ui.VaultViewModel
import com.example.ui.screens.VaultNavHost
import com.example.ui.theme.VaultLockTheme

class MainActivity : FragmentActivity() {
    private val viewModel: VaultViewModel by viewModels()

    private val ENABLE_FLAG_SECURE = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ENABLE_FLAG_SECURE) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val navController = rememberNavController()

            VaultLockTheme(themeMode = uiState.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VaultNavHost(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppBackgrounded()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.onUserActivity()
    }
}
