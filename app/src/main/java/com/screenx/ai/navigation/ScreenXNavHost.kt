package com.screenx.ai.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.screenx.ai.R
import com.screenx.ai.presentation.common.PlaceholderScreen
import com.screenx.ai.presentation.home.HomeScreen

@Composable
fun ScreenXNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(220)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(220)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(onNavigate = { navController.navigateToTopLevel(it.route) })
        }
        composable(Screen.Recordings.route) {
            PlaceholderScreen(stringResource(R.string.nav_recordings), Icons.Rounded.VideoLibrary, phase = 9)
        }
        composable(Screen.Screenshots.route) {
            PlaceholderScreen(stringResource(R.string.nav_screenshots), Icons.Rounded.Image, phase = 9)
        }
        composable(Screen.Editor.route) {
            PlaceholderScreen(stringResource(R.string.title_editor), Icons.Rounded.ContentCut, phase = 10)
        }
        composable(Screen.Settings.route) {
            PlaceholderScreen(stringResource(R.string.nav_settings), Icons.Rounded.Settings, phase = 11)
        }
    }
}
