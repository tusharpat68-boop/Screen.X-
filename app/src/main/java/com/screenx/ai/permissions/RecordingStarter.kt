package com.screenx.ai.permissions

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.screenx.ai.R
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.service.RecordingService

/**
 * Returns a function that runs the full start flow:
 * 1) notification permission (Android 13+), 2) microphone permission if audio is selected,
 * 3) system screen-capture consent, 4) start the service.
 * The consent dialog is always shown by Android and cannot be skipped.
 */
@Composable
fun rememberRecordingStarter(audioSource: AudioSource): () -> Unit {
    val context = LocalContext.current
    val requested by rememberUpdatedState(audioSource)
    var effective by remember { mutableStateOf(AudioSource.NONE) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            RecordingService.start(context, result.resultCode, data, effective)
        }
    }

    fun launchConsent() {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        consentLauncher.launch(manager.createScreenCaptureIntent())
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            effective = AudioSource.NONE
            Toast.makeText(context, R.string.perm_audio_denied, Toast.LENGTH_LONG).show()
        }
        launchConsent()
    }

    fun continueAfterNotifications() {
        effective = requested
        if (effective != AudioSource.NONE && !PermissionHelper.hasAudioPermission(context)) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            launchConsent()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, R.string.perm_notification_denied, Toast.LENGTH_LONG).show()
        }
        // Capture still works without the notification permission, so continue either way.
        continueAfterNotifications()
    }

    return {
        if (PermissionHelper.needsNotificationPermission(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueAfterNotifications()
        }
    }
}
