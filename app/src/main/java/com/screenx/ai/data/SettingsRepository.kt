package com.screenx.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.FloatingConfig
import com.screenx.ai.domain.RecordingConfig
import com.screenx.ai.domain.VideoOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val RESOLUTION = intPreferencesKey("resolution_p")
        val FPS = intPreferencesKey("fps")
        val BITRATE_KBPS = intPreferencesKey("bitrate_kbps")
        val ORIENTATION = stringPreferencesKey("orientation")
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val AUDIO_KBPS = intPreferencesKey("audio_kbps")
        val FLOAT_ENABLED = booleanPreferencesKey("float_enabled")
        val FLOAT_SIZE = intPreferencesKey("float_size")
        val FLOAT_OPACITY = intPreferencesKey("float_opacity")
        val FLOAT_X = intPreferencesKey("float_x")
        val FLOAT_Y = intPreferencesKey("float_y")
    }

    val recordingConfig: Flow<RecordingConfig> = store.data.map { p ->
        RecordingConfig(
            resolutionP = p[Keys.RESOLUTION] ?: 720,
            fps = p[Keys.FPS] ?: 30,
            bitrateKbps = p[Keys.BITRATE_KBPS] ?: 0,
            orientation = runCatching { VideoOrientation.valueOf(p[Keys.ORIENTATION] ?: "AUTO") }
                .getOrDefault(VideoOrientation.AUTO),
            audioSource = runCatching { AudioSource.valueOf(p[Keys.AUDIO_SOURCE] ?: "NONE") }
                .getOrDefault(AudioSource.NONE),
            audioBitrateKbps = p[Keys.AUDIO_KBPS] ?: 128
        )
    }

    val floatingConfig: Flow<FloatingConfig> = store.data.map { p ->
        FloatingConfig(
            enabled = p[Keys.FLOAT_ENABLED] ?: false,
            sizeLevel = p[Keys.FLOAT_SIZE] ?: 1,
            opacityPercent = p[Keys.FLOAT_OPACITY] ?: 100,
            x = p[Keys.FLOAT_X] ?: -1,
            y = p[Keys.FLOAT_Y] ?: -1
        )
    }

    suspend fun setResolution(p: Int) { store.edit { it[Keys.RESOLUTION] = p } }
    suspend fun setFps(fps: Int) { store.edit { it[Keys.FPS] = fps } }
    suspend fun setBitrateKbps(kbps: Int) { store.edit { it[Keys.BITRATE_KBPS] = kbps } }
    suspend fun setOrientation(o: VideoOrientation) { store.edit { it[Keys.ORIENTATION] = o.name } }
    suspend fun setAudioSource(s: AudioSource) { store.edit { it[Keys.AUDIO_SOURCE] = s.name } }
    suspend fun setAudioBitrateKbps(kbps: Int) { store.edit { it[Keys.AUDIO_KBPS] = kbps } }

    suspend fun setFloatingEnabled(enabled: Boolean) { store.edit { it[Keys.FLOAT_ENABLED] = enabled } }
    suspend fun setFloatingSize(level: Int) { store.edit { it[Keys.FLOAT_SIZE] = level } }
    suspend fun setFloatingOpacity(percent: Int) { store.edit { it[Keys.FLOAT_OPACITY] = percent } }
    suspend fun setFloatingPosition(x: Int, y: Int) {
        store.edit {
            it[Keys.FLOAT_X] = x
            it[Keys.FLOAT_Y] = y
        }
    }
}
