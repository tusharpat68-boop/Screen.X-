package com.screenx.ai.recording

import android.media.MediaCodecInfo
import android.media.MediaCodecList

/** Asks the device's H.264 encoders what they can really do. */
object EncoderCapabilities {
    private const val MIME = "video/avc"

    private val videoCaps: List<MediaCodecInfo.VideoCapabilities> by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(MIME, true) } }
            .mapNotNull { info ->
                runCatching { info.getCapabilitiesForType(MIME).videoCapabilities }.getOrNull()
            }
    }

    fun supports(width: Int, height: Int, fps: Int): Boolean = videoCaps.any {
        it.areSizeAndRateSupported(width, height, fps.toDouble()) ||
            it.areSizeAndRateSupported(height, width, fps.toDouble())
    }
}
