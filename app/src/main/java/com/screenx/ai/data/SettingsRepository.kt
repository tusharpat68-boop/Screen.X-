package com.screenx.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.CameraLens
import com.screenx.ai.domain.FacecamConfig
import com.screenx.ai.domain.FacecamShape
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
        val CAM_ENABLED = booleanPreferencesKey("cam_enabled")
        val CAM_LENS = stringPreferencesKey("cam_lens")
        val CAM_SHAPE = stringPreferencesKey("cam_shape")
        val CAM_SIZE = intPreferencesKey("cam_size")
        val CAM_X = intPreferencesKey("cam_x")
        val CAM_Y = intPreferencesKey("cam_y")
        val DRAW_ENABLED = booleanPreferencesKey("draw_enabled")
        val FAVORITES = stringSetPreferencesKey("favorite_files")
        val SHOT_DELAY = intPreferencesKey("screenshot_delay_sec")
        val COUNTDOWN = intPreferencesKey("recording_countdown_sec")
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val HIDE_FLOATING = booleanPreferencesKey("hide_floating_during_recording")
        val WATERMARK = booleanPreferencesKey("watermark_enabled")
        val SAVE_TREE_URI = stringPreferencesKey("save_tree_uri")
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

    val facecamConfig: Flow<FacecamConfig> = store.data.map { p ->
        FacecamConfig(
            enabled = p[Keys.CAM_ENABLED] ?: false,
            lens = runCatching { CameraLens.valueOf(p[Keys.CAM_LENS] ?: "FRONT") }
                .getOrDefault(CameraLens.FRONT),
            shape = runCatching { FacecamShape.valueOf(p[Keys.CAM_SHAPE] ?: "CIRCLE") }
                .getOrDefault(FacecamShape.CIRCLE),
            sizeLevel = p[Keys.CAM_SIZE] ?: 1,
            x = p[Keys.CAM_X] ?: -1,
            y = p[Keys.CAM_Y] ?: -1
        )
    }

    val drawingEnabled: Flow<Boolean> = store.data.map { p -> p[Keys.DRAW_ENABLED] ?: false }

    val favorites: Flow<Set<String>> = store.data.map { p -> p[Keys.FAVORITES] ?: emptySet() }

    val screenshotDelaySec: Flow<Int> = store.data.map { p -> p[Keys.SHOT_DELAY] ?: 3 }
    val countdownSeconds: Flow<Int> = store.data.map { p -> p[Keys.COUNTDOWN] ?: 3 }
    val themeMode: Flow<String> = store.data.map { p -> p[Keys.THEME] ?: "SYSTEM" }
    val language: Flow<String> = store.data.map { p -> p[Keys.LANGUAGE] ?: "SYSTEM" }
    val hideFloatingDuringRecording: Flow<Boolean> = store.data.map { p -> p[Keys.HIDE_FLOATING] ?: false }
    val watermarkEnabled: Flow<Boolean> = store.data.map { p -> p[Keys.WATERMARK] ?: false }
    val saveTreeUri: Flow<String?> = store.data.map { p -> p[Keys.SAVE_TREE_URI] }

    suspend fun setFavorite(fileName: String, favorite: Boolean) {
        store.edit {
            val current = it[Keys.FAVORITES] ?: emptySet()
            it[Keys.FAVORITES] = if (favorite) current + fileName else current - fileName
        }
    }

    suspend fun renameFavorite(oldName: String, newName: String) {
        store.edit {
            val current = it[Keys.FAVORITES] ?: emptySet()
            if (oldName in current) it[Keys.FAVORITES] = current - oldName + newName
        }
    }

    suspend fun setScreenshotDelay(seconds: Int) { store.edit { it[Keys.SHOT_DELAY] = seconds.coerceIn(0, 10) } }
    suspend fun setCountdownSeconds(seconds: Int) { store.edit { it[Keys.COUNTDOWN] = seconds.coerceIn(0, 10) } }
    suspend fun setThemeMode(mode: String) { store.edit { it[Keys.THEME] = mode } }
    suspend fun setLanguage(language: String) { store.edit { it[Keys.LANGUAGE] = language } }
    suspend fun setHideFloatingDuringRecording(enabled: Boolean) { store.edit { it[Keys.HIDE_FLOATING] = enabled } }
    suspend fun setWatermarkEnabled(enabled: Boolean) { store.edit { it[Keys.WATERMARK] = enabled } }
    suspend fun setSaveTreeUri(uri: String?) { store.edit { if (uri == null) it.remove(Keys.SAVE_TREE_URI) else it[Keys.SAVE_TREE_URI] = uri } }

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

    suspend fun setDrawingEnabled(enabled: Boolean) { store.edit { it[Keys.DRAW_ENABLED] = enabled } }

    suspend fun setFacecamEnabled(enabled: Boolean) { store.edit { it[Keys.CAM_ENABLED] = enabled } }
    suspend fun setFacecamLens(lens: CameraLens) { store.edit { it[Keys.CAM_LENS] = lens.name } }
    suspend fun setFacecamShape(shape: FacecamShape) { store.edit { it[Keys.CAM_SHAPE] = shape.name } }
    suspend fun setFacecamSize(level: Int) { store.edit { it[Keys.CAM_SIZE] = level } }
    suspend fun setFacecamPosition(x: Int, y: Int) {
        store.edit {
            it[Keys.CAM_X] = x
            it[Keys.CAM_Y] = y
        }
    }
}
