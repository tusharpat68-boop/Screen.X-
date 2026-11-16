package com.screenx.ai.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.screenx.ai.ScreenXApp
import com.screenx.ai.navigation.ScreenXNavHost
import com.screenx.ai.presentation.components.ScreenXBottomBar
import com.screenx.ai.presentation.theme.ScreenXTheme

@Composable
fun ScreenXRoot() {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as ScreenXApp).settingsRepository }
    val themeMode by repository.themeMode.collectAsState(initial = "SYSTEM")
    val dark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val navController = rememberNavController()
    ScreenXTheme(darkTheme = dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { ScreenXBottomBar(navController) }
        ) { innerPadding ->
            ScreenXNavHost(navController, Modifier.padding(innerPadding))
        }
    }
}
