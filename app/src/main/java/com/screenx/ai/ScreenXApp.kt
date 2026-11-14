package com.screenx.ai

import android.app.Application
import com.screenx.ai.data.SettingsRepository
import com.screenx.ai.recording.RecordingSession
import com.screenx.ai.storage.RecordingStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application entry point and simple app-wide holder (manual dependency injection). */
class ScreenXApp : Application() {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Remove partial recordings left behind by a crash or a killed process.
        appScope.launch {
            if (!RecordingSession.state.value.isActive) {
                runCatching { RecordingStorage.cleanupIncomplete(this@ScreenXApp) }
            }
        }
    }
}
