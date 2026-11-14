package com.screenx.ai.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object ScreenXGradients {
    val brand: Brush = Brush.linearGradient(listOf(ScreenXPrimary, ScreenXSecondary))
    val brandWithAccent: Brush =
        Brush.linearGradient(listOf(ScreenXPrimary, ScreenXSecondary, ScreenXAccent))
    val glow: Brush =
        Brush.radialGradient(listOf(ScreenXPrimary.copy(alpha = 0.30f), Color.Transparent))
}
