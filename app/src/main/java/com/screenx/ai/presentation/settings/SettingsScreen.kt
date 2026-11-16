package com.screenx.ai.presentation.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.screenx.ai.ScreenXApp
import com.screenx.ai.domain.AudioSource
import com.screenx.ai.domain.CameraLens
import com.screenx.ai.domain.FacecamShape
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as ScreenXApp
    val repo = remember { app.settingsRepository }
    val scope = rememberCoroutineScope()
    val recording by repo.recordingConfig.collectAsState(initial = com.screenx.ai.domain.RecordingConfig())
    val floating by repo.floatingConfig.collectAsState(initial = com.screenx.ai.domain.FloatingConfig())
    val facecam by repo.facecamConfig.collectAsState(initial = com.screenx.ai.domain.FacecamConfig())
    val countdown by repo.countdownSeconds.collectAsState(initial = 3)
    val theme by repo.themeMode.collectAsState(initial = "SYSTEM")
    val language by repo.language.collectAsState(initial = "SYSTEM")
    val hideFloating by repo.hideFloatingDuringRecording.collectAsState(initial = false)
    val watermark by repo.watermarkEnabled.collectAsState(initial = false)
    val saveUri by repo.saveTreeUri.collectAsState(initial = null)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.isSuccess
            if (persisted) scope.launch { repo.setSaveTreeUri(uri.toString()) }
        }
    }
    val bn = language == "BN"
    fun label(en: String, bangla: String) = if (bn) bangla else en

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label("Settings", "সেটিংস"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label("Personalize your ScreenX experience", "ScreenX আপনার পছন্দমতো সাজান"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        SettingsSection(label("Video recording", "ভিডিও রেকর্ডিং"), "🎬") {
            ChoiceRow(label("Resolution", "রেজোলিউশন"), listOf("480p", "720p", "1080p"), "${recording.resolutionP}p") { scope.launch { repo.setResolution(it.removeSuffix("p").toInt()) } }
            ChoiceRow(label("Frame rate", "ফ্রেম রেট"), listOf("24 FPS", "30 FPS", "60 FPS"), "${recording.fps} FPS") { scope.launch { repo.setFps(it.substringBefore(' ').toInt()) } }
            val bitrates = listOf("Auto", "4 Mbps", "8 Mbps", "12 Mbps", "16 Mbps")
            val selectedBitrate = when (recording.bitrateKbps) { 4000 -> "4 Mbps"; 8000 -> "8 Mbps"; 12000 -> "12 Mbps"; 16000 -> "16 Mbps"; else -> "Auto" }
            ChoiceRow(label("Video bitrate", "ভিডিও বিটরেট"), bitrates, selectedBitrate) { scope.launch { repo.setBitrateKbps(if (it == "Auto") 0 else it.substringBefore(' ').toInt() * 1000) } }
            val audioLabel = when (recording.audioSource) { AudioSource.NONE -> "Off"; AudioSource.MICROPHONE -> "Microphone"; AudioSource.INTERNAL -> "Internal"; AudioSource.BOTH -> "Mic + Internal" }
            ChoiceRow(label("Audio source", "অডিও সোর্স"), listOf("Off", "Microphone", "Internal", "Mic + Internal"), audioLabel) {
                scope.launch { repo.setAudioSource(when (it) { "Microphone" -> AudioSource.MICROPHONE; "Internal" -> AudioSource.INTERNAL; "Mic + Internal" -> AudioSource.BOTH; else -> AudioSource.NONE }) }
            }
            ChoiceRow(label("Start countdown", "শুরুর কাউন্টডাউন"), listOf("Off", "3 sec", "5 sec", "10 sec"), if (countdown == 0) "Off" else "$countdown sec") { scope.launch { repo.setCountdownSeconds(if (it == "Off") 0 else it.substringBefore(' ').toInt()) } }
            Text(label("Internal audio requires Android 10+ and app playback capture support.", "ইন্টারনাল অডিওর জন্য Android 10+ এবং অ্যাপের অনুমতি প্রয়োজন।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection(label("Floating controls", "ফ্লোটিং কন্ট্রোল"), "◉") {
            SettingSwitch(label("Enable floating controls", "ফ্লোটিং কন্ট্রোল চালু"), floating.enabled) { scope.launch { repo.setFloatingEnabled(it) } }
            ChoiceRow(label("Bubble size", "বাবলের আকার"), listOf("Small", "Medium", "Large"), listOf("Small", "Medium", "Large")[floating.sizeLevel.coerceIn(0, 2)]) { scope.launch { repo.setFloatingSize(listOf("Small", "Medium", "Large").indexOf(it)) } }
            ChoiceRow(label("Opacity", "স্বচ্ছতা"), listOf("50%", "75%", "100%"), "${floating.opacityPercent}%") { scope.launch { repo.setFloatingOpacity(it.removeSuffix("%").toInt()) } }
            SettingSwitch(label("Hide controls while recording", "রেকর্ডিংয়ের সময় কন্ট্রোল লুকান"), hideFloating) { scope.launch { repo.setHideFloatingDuringRecording(it) } }
            Text(label("Overlay permission is requested only when an overlay feature is enabled.", "ওভারলে ফিচার চালু করলে তবেই প্রয়োজনীয় অনুমতি চাওয়া হবে।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection(label("Camera overlay", "ক্যামেরা ওভারলে"), "📷") {
            SettingSwitch(label("Enable face camera", "ফেস ক্যামেরা চালু"), facecam.enabled) { scope.launch { repo.setFacecamEnabled(it) } }
            ChoiceRow(label("Camera", "ক্যামেরা"), listOf("Front", "Back"), if (facecam.lens == CameraLens.FRONT) "Front" else "Back") { scope.launch { repo.setFacecamLens(if (it == "Front") CameraLens.FRONT else CameraLens.BACK) } }
            ChoiceRow(label("Camera shape", "ক্যামেরার আকৃতি"), listOf("Circle", "Rectangle"), if (facecam.shape == FacecamShape.CIRCLE) "Circle" else "Rectangle") { scope.launch { repo.setFacecamShape(if (it == "Circle") FacecamShape.CIRCLE else FacecamShape.RECTANGLE) } }
            ChoiceRow(label("Camera bubble size", "ক্যামেরা বাবলের আকার"), listOf("Small", "Medium", "Large", "Extra large"), listOf("Small", "Medium", "Large", "Extra large")[facecam.sizeLevel.coerceIn(0, 3)]) { scope.launch { repo.setFacecamSize(listOf("Small", "Medium", "Large", "Extra large").indexOf(it)) } }
            Text(label("Face camera needs camera permission and display-over-other-apps permission. Device support may vary.", "ফেস ক্যামেরার জন্য ক্যামেরা ও অন্য অ্যাপের ওপর প্রদর্শনের অনুমতি প্রয়োজন। ডিভাইসভেদে সমর্থন আলাদা হতে পারে।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection(label("Storage and appearance", "স্টোরেজ ও চেহারা"), "▣") {
            Text(label("Preferred folder", "পছন্দের ফোল্ডার"), style = MaterialTheme.typography.titleSmall)
            Text(if (saveUri == null) label("Default: Movies/ScreenX", "ডিফল্ট: Movies/ScreenX") else label("A custom folder preference is saved", "কাস্টম ফোল্ডার পছন্দ সংরক্ষিত আছে"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { folderPicker.launch(null) }) { Text(label("Choose folder", "ফোল্ডার নির্বাচন")) }
            if (saveUri != null) Button(onClick = { scope.launch { repo.setSaveTreeUri(null) } }) { Text(label("Clear folder preference", "ফোল্ডার পছন্দ মুছুন")) }
            Text(label("New screen recordings use the selected folder. If access is revoked or the folder is unavailable, recording will fail rather than silently switch folders. Edited video exports use the selected folder; extracted audio still uses Music/ScreenX.", "নতুন স্ক্রিন রেকর্ডিং ও এডিট করা ভিডিও নির্বাচিত ফোল্ডারে সেভ হবে। অনুমতি বাতিল বা ফোল্ডার অনুপলব্ধ হলে সেভ ব্যর্থ হবে। Extracted audio Music/ScreenX-এ সেভ হবে।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceRow(label("Theme", "থিম"), listOf("System", "Dark", "Light"), theme.lowercase().replaceFirstChar { it.uppercase() }) { scope.launch { repo.setThemeMode(it.uppercase()) } }
            ChoiceRow(label("Language", "ভাষা"), listOf("System", "English", "বাংলা"), when (language) { "EN" -> "English"; "BN" -> "বাংলা"; else -> "System" }) { scope.launch { repo.setLanguage(when (it) { "English" -> "EN"; "বাংলা" -> "BN"; else -> "SYSTEM" }) } }
            Text(label("Language preference currently changes Settings labels only; full-app translation is planned separately.", "ভাষা পছন্দটি আপাতত শুধু Settings-এর লেখা বদলায়; পুরো অ্যাপের অনুবাদ পরে যুক্ত হবে।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingSwitch(label("Watermark preference", "ওয়াটারমার্ক পছন্দ"), watermark) { scope.launch { repo.setWatermarkEnabled(it) } }
            Text(label("Watermark is burned into edited video exports when enabled. It is not added to live screen recordings.", "ওয়াটারমার্ক চালু থাকলে এডিট করা ভিডিও এক্সপোর্টে এটি বসবে। সরাসরি স্ক্রিন রেকর্ডিংয়ে এটি যুক্ত হয় না।"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection(label("Privacy and about", "গোপনীয়তা ও পরিচিতি"), "ⓘ") {
            Text(label("Your recordings stay on your device. ScreenX AI does not upload your videos or screenshots.", "আপনার রেকর্ডিং আপনার ডিভাইসেই থাকে। ScreenX AI আপনার ভিডিও বা স্ক্রিনশট আপলোড করে না।"), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("ScreenX AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label("Professional Android screen recorder", "প্রফেশনাল Android স্ক্রিন রেকর্ডার"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Version 0.19.0 · Phase 19", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SettingsSection(title: String, icon: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(icon, style = MaterialTheme.typography.titleLarge); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            content()
        }
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { option -> FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option, style = MaterialTheme.typography.labelSmall) }) }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
