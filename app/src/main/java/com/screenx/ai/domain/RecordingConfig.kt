package com.screenx.ai.domain

enum class VideoOrientation { AUTO, PORTRAIT, LANDSCAPE }

/**
 * User-chosen recording settings.
 * [resolutionP] is the short side in pixels (480, 720, 1080...). [bitrateKbps] 0 means automatic.
 */
data class RecordingConfig(
    val resolutionP: Int = 720,
    val fps: Int = 30,
    val bitrateKbps: Int = 0,
    val orientation: VideoOrientation = VideoOrientation.AUTO,
    val audioSource: AudioSource = AudioSource.NONE,
    val audioBitrateKbps: Int = 128
) {
    companion object {
        val RESOLUTIONS = listOf(480, 720, 1080, 1440, 2160)
        val FPS_OPTIONS = listOf(24, 30, 60)
        val AUDIO_BITRATES = listOf(64, 128, 192)
    }
}

/** Qualities this device and its encoder can actually do. */
data class QualityOptions(
    val resolutions: List<Int> = listOf(480, 720, 1080),
    val fps: List<Int> = listOf(24, 30, 60),
    val audioSources: List<AudioSource> = listOf(AudioSource.NONE, AudioSource.MICROPHONE)
)
