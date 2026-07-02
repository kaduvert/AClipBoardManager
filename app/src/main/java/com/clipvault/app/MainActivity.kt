package com.clipvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clipvault.app.ui.ClipViewModel
import com.clipvault.app.ui.MainScreen
import com.clipvault.app.ui.SettingsScreen
import com.clipvault.app.ui.theme.ClipVaultTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ClipViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipVaultTheme {
                ClipVaultNavHost(viewModel)
            }
        }
    }
}

private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"

@Composable
private fun ClipVaultNavHost(viewModel: ClipViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_MAIN) {
        composable(ROUTE_MAIN) {
            MainScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
