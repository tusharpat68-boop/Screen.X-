package com.screenx.ai.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.screenx.ai.navigation.ScreenXNavHost
import com.screenx.ai.presentation.components.ScreenXBottomBar

/** Root composable: scaffold + bottom navigation + nav host. */
@Composable
fun ScreenXRoot() {
    val navController = rememberNavController()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { ScreenXBottomBar(navController) }
    ) { innerPadding ->
        ScreenXNavHost(navController, Modifier.padding(innerPadding))
    }
}
