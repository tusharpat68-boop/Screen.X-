package com.screenx.ai.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.screenx.ai.R
import com.screenx.ai.ScreenXApp
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.FloatingConfig
import com.screenx.ai.domain.RecordingState
import com.screenx.ai.domain.RecordingStatus
import com.screenx.ai.overlay.FloatingControls
import com.screenx.ai.permissions.PermissionHelper
import com.screenx.ai.recording.RecordingSession
import com.screenx.ai.recording.ScreenRecorderEngine
import com.screenx.ai.recording.StopOutcome
import com.screenx.ai.storage.StorageInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the MediaProjection token, the recorder engine, the timer
 * and the floating controller.
 * Flow: consent -> startForeground -> projection -> engine.start -> timer -> stop/save.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var engine: ScreenRecorderEngine? = null
    private var floating: FloatingControls? = null
    private var controlsHidden = false
    private var tickerJob: Job? = null
    private var accumulatedMs = 0L
    private var segmentStartMs = 0L
    private var isStopping = false
    private var activeAudio = AudioSource.NONE

    private val settings get() = (application as ScreenXApp).settingsRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != ACTION_START && !RecordingSession.state.value.isActive) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_STOP -> stopSession()
            ACTION_TOGGLE_CONTROLS -> toggleControls()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        NotificationHelper.ensureChannel(this)
        val current = RecordingSession.state.value

        // Already running: just satisfy the startForegroundService() contract again.
        if (current.isActive) {
            promote(buildNotification(current.status, current.elapsedMs), activeAudio)
            return
        }

        // Audio is only used if the permission was really granted.
        var audio = runCatching {
            AudioSource.valueOf(intent.getStringExtra(EXTRA_AUDIO_SOURCE) ?: "NONE")
        }.getOrDefault(AudioSource.NONE)
        if (audio != AudioSource.NONE && !PermissionHelper.hasAudioPermission(this)) {
            audio = AudioSource.NONE
        }
        activeAudio = audio

        RecordingSession.update { RecordingState(status = RecordingStatus.STARTING) }
        try {
            promote(buildNotification(RecordingStatus.RECORDING, 0L), audio)
        } catch (e: Exception) {
            failAndStop(getString(R.string.error_service_start, e.message ?: e.javaClass.simpleName))
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
        if (resultCode != Activity.RESULT_OK || data == null) {
            failAndStop(getString(R.string.error_no_consent))
            return
        }

        // On Android 14+ the projection must be obtained AFTER startForeground(mediaProjection).
        val manager = getSystemService(MediaProjectionManager::class.java)
        val newProjection = try {
            manager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            null
        }
        if (newProjection == null) {
            failAndStop(getString(R.string.error_projection))
            return
        }

        // Must be registered before capture starts (required on Android 14+).
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                // User ended sharing from the system UI, or the system revoked it: save what we have.
                stopSession()
            }
        }
        newProjection.registerCallback(callback, Handler(Looper.getMainLooper()))
        projection = newProjection
        projectionCallback = callback

        scope.launch {
            val saved = settings.recordingConfig.first()
            val floatingConfig = settings.floatingConfig.first()
            if (isStopping) return@launch
            val config = saved.copy(audioSource = audio)

            if (StorageInfo.read().freeBytes < MIN_START_BYTES) {
                failAndStop(getString(R.string.error_low_storage))
                return@launch
            }

            val newEngine = ScreenRecorderEngine(applicationContext, newProjection)
            newEngine.onError = { message -> failAndStop(message) }
            newEngine.onWarning = { message ->
                RecordingSession.update { it.copy(notice = message) }
            }
            try {
                newEngine.start(config)
            } catch (e: Exception) {
                newEngine.abort()
                failAndStop(e.message ?: getString(R.string.error_projection))
                return@launch
            }
            engine = newEngine
            beginTiming()
            showFloating(floatingConfig)
        }
    }

    // ---- floating controls ----

    private fun showFloating(config: FloatingConfig) {
        if (!config.enabled) return
        if (!Settings.canDrawOverlays(this)) {
            RecordingSession.update { it.copy(notice = getString(R.string.warn_overlay_missing)) }
            return
        }
        val controls = FloatingControls(
            context = applicationContext,
            config = config,
            onPause = { pauseSession() },
            onResume = { resumeSession() },
            onStop = { stopSession() },
            onHide = { toggleControls() },
            onMoved = { x, y -> scope.launch { settings.setFloatingPosition(x, y) } }
        )
        if (controls.show()) {
            floating = controls
            val state = RecordingSession.state.value
            controls.update(state.status, state.elapsedMs)
            updateNotification()
        } else {
            RecordingSession.update { it.copy(notice = getString(R.string.warn_overlay_failed)) }
        }
    }

    private fun toggleControls() {
        val controls = floating ?: return
        controlsHidden = !controlsHidden
        controls.setVisible(!controlsHidden)
        updateNotification()
    }

    private fun dismissFloating() {
        floating?.dismiss()
        floating = null
        controlsHidden = false
    }

    private fun controlsToggle(): ControlsToggle = when {
        floating == null -> ControlsToggle.NONE
        controlsHidden -> ControlsToggle.SHOW
        else -> ControlsToggle.HIDE
    }

    private fun buildNotification(status: RecordingStatus, elapsedMs: Long): Notification =
        NotificationHelper.build(this, status, elapsedMs, controlsToggle())

    // ---- foreground + timer ----

    private fun promote(notification: Notification, audio: AudioSource) {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        // Android 14+ requires the microphone type whenever audio is captured from a service.
        if (audio != AudioSource.NONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        ServiceCompat.startForeground(this, NotificationHelper.NOTIFICATION_ID, notification, types)
    }

    private fun beginTiming() {
        accumulatedMs = 0L
        segmentStartMs = SystemClock.elapsedRealtime()
        RecordingSession.update { it.copy(status = RecordingStatus.RECORDING, elapsedMs = 0L) }
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                tick()
                delay(1000)
            }
        }
    }

    private fun tick() {
        // Low storage: stop and save before the disk fills up.
        if (StorageInfo.read().freeBytes < LOW_STORAGE_STOP_BYTES) {
            stopSession(getString(R.string.notice_low_storage))
            return
        }
        val elapsed = accumulatedMs + (SystemClock.elapsedRealtime() - segmentStartMs)
        RecordingSession.update { it.copy(elapsedMs = elapsed) }
        floating?.update(RecordingStatus.RECORDING, elapsed)
        updateNotification()
    }

    private fun pauseSession() {
        if (RecordingSession.state.value.status != RecordingStatus.RECORDING) return
        try {
            engine?.pause()
        } catch (e: IllegalStateException) {
            return
        }
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStartMs
        tickerJob?.cancel()
        RecordingSession.update { it.copy(status = RecordingStatus.PAUSED, elapsedMs = accumulatedMs) }
        floating?.update(RecordingStatus.PAUSED, accumulatedMs)
        updateNotification()
    }

    private fun resumeSession() {
        if (RecordingSession.state.value.status != RecordingStatus.PAUSED) return
        try {
            engine?.resume()
        } catch (e: IllegalStateException) {
            return
        }
        segmentStartMs = SystemClock.elapsedRealtime()
        RecordingSession.update { it.copy(status = RecordingStatus.RECORDING) }
        floating?.update(RecordingStatus.RECORDING, accumulatedMs)
        startTicker()
    }

    /** Finishes the recording and saves the video. */
    private fun stopSession(notice: String? = null) {
        if (isStopping) return
        isStopping = true
        // Remove the bubble first so it is not in the last frames of the video.
        dismissFloating()
        val finalNotice = notice ?: RecordingSession.state.value.notice
        RecordingSession.update { it.copy(status = RecordingStatus.STOPPING) }
        tickerJob?.cancel()

        // The recorder must be stopped BEFORE the projection.
        val outcome = engine?.stop()
        engine = null
        releaseProjection()

        when (outcome) {
            is StopOutcome.Saved ->
                RecordingSession.saved(outcome.displayName, outcome.uri?.toString(), finalNotice)
            is StopOutcome.Failed -> RecordingSession.fail(outcome.message)
            null -> RecordingSession.reset()
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Aborts the session, deletes any partial file and shows [message]. */
    private fun failAndStop(message: String) {
        if (isStopping) return
        isStopping = true
        dismissFloating()
        tickerJob?.cancel()
        engine?.abort()
        engine = null
        releaseProjection()
        RecordingSession.fail(message)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseProjection() {
        projectionCallback?.let { projection?.unregisterCallback(it) }
        projection?.stop()
        projection = null
        projectionCallback = null
    }

    private fun updateNotification() {
        val state = RecordingSession.state.value
        val notification = buildNotification(state.status, state.elapsedMs)
        getSystemService(NotificationManager::class.java)
            .notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        scope.cancel()
        dismissFloating()
        // Safety net if the system destroys us mid-recording.
        engine?.abort()
        engine = null
        releaseProjection()
        if (RecordingSession.state.value.isActive) RecordingSession.reset()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.screenx.ai.action.START"
        const val ACTION_PAUSE = "com.screenx.ai.action.PAUSE"
        const val ACTION_RESUME = "com.screenx.ai.action.RESUME"
        const val ACTION_STOP = "com.screenx.ai.action.STOP"
        const val ACTION_TOGGLE_CONTROLS = "com.screenx.ai.action.TOGGLE_CONTROLS"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_AUDIO_SOURCE = "extra_audio_source"

        private const val MIN_START_BYTES = 100L * 1024 * 1024
        private const val LOW_STORAGE_STOP_BYTES = 50L * 1024 * 1024

        /** Starts the service with the result of the screen-capture consent dialog. */
        fun start(context: Context, resultCode: Int, data: Intent, audioSource: AudioSource) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
                .putExtra(EXTRA_AUDIO_SOURCE, audioSource.name)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Sends pause / resume / stop to a running service. */
        fun send(context: Context, action: String) {
            context.startService(Intent(context, RecordingService::class.java).setAction(action))
        }
    }
}
