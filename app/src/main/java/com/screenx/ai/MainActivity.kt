package com.screenx.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.screenx.ai.presentation.ScreenXRoot
import com.screenx.ai.presentation.theme.ScreenXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenXTheme {
                ScreenXRoot()
            }
        }
    }
}
