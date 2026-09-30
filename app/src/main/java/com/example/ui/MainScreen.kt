package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioSourceOption
import com.example.model.CodecCapabilityReport
import com.example.model.PresetType
import com.example.model.RecordingConfig
import com.example.model.RecordingStatus
import com.example.storage.MediaStoreHelper
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CardDark
import com.example.ui.theme.CrimsonRecord
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningAmber

@Composable
fun MainScreen(
    config: RecordingConfig,
    codecReport: CodecCapabilityReport,
    recordingStatus: RecordingStatus,
    recordingsCount: Int,
    onStartRecordingClicked: () -> Unit,
    onPauseClicked: () -> Unit,
    onResumeClicked: () -> Unit,
    onStopClicked: () -> Unit,
    onSelectPreset: (PresetType) -> Unit,
    onUpdateConfig: (RecordingConfig) -> Unit,
    onSelectAudioSource: (AudioSourceOption) -> Unit,
    onToggleGamingMode: (Boolean) -> Unit,
    onToggleCountdown: (Boolean) -> Unit,
    onToggleFloatingControls: (Boolean) -> Unit,
    onOpenRecordings: () -> Unit,
    userMessage: String?,
    onClearUserMessage: () -> Unit
) {
    var showCustomSettingsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearUserMessage()
        }
    }

    val isRecordingActive = recordingStatus is RecordingStatus.Recording
    val isPaused = recordingStatus is RecordingStatus.Paused

    Scaffold(
        containerColor = CarbonDark,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: GAMECAPTURE LITE
            HeaderSection(onOpenRecordings = onOpenRecordings, recordingsCount = recordingsCount)

            // Current Recording Banner if active
            if (isRecordingActive || isPaused) {
                ActiveRecordingHUD(
                    recordingStatus = recordingStatus,
                    onPause = onPauseClicked,
                    onResume = onResumeClicked,
                    onStop = onStopClicked
                )
            } else {
                // Preset Summary Banner & Big Start Button
                PresetSummaryBanner(config = config)

                StartRecordingButton(
                    onClick = onStartRecordingClicked,
                    gamingMode = config.gamingModeEnabled
                )
            }

            // Hardware Codec Status Card
            HardwareStatusCard(codecReport = codecReport)

            // Presets Selection
            Text(
                text = "RECORDING PRESETS",
                color = NeonEmerald,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            PresetCardsGrid(
                selectedPreset = config.preset,
                onSelectPreset = onSelectPreset,
                onOpenCustom = { showCustomSettingsDialog = true }
            )

            // Audio Configuration Section
            Text(
                text = "AUDIO CAPTURE",
                color = NeonEmerald,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            AudioSourceSelector(
                selectedSource = config.audioSource,
                supportsInternal = codecReport.supportsInternalAudio,
                onSelectSource = onSelectAudioSource
            )

            // Gaming Mode & Optimizations
            Text(
                text = "PERFORMANCE CONTROLS",
                color = NeonEmerald,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Gaming Mode Toggle
                    ToggleRow(
                        icon = Icons.Default.Speed,
                        iconTint = NeonEmerald,
                        title = "Gaming Mode",
                        description = "Minimal RAM/CPU overhead. Disables unnecessary animations and background tasks.",
                        checked = config.gamingModeEnabled,
                        onCheckedChange = onToggleGamingMode,
                        tag = "gaming_mode_switch"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(CardDark)
                    )

                    // 3-Second Countdown
                    ToggleRow(
                        icon = Icons.Default.Timer,
                        iconTint = ElectricCyan,
                        title = "3-Second Countdown",
                        description = "Cues you before recording captures your PUBG match.",
                        checked = config.countdownEnabled,
                        onCheckedChange = onToggleCountdown,
                        tag = "countdown_switch"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(CardDark)
                    )

                    // Floating Controls Overlay
                    ToggleRow(
                        icon = Icons.Default.Videocam,
                        iconTint = WarningAmber,
                        title = "Floating Control Widget",
                        description = "Draggable mini-widget over PUBG with timer, pause, and stop controls.",
                        checked = config.floatingControlsEnabled,
                        onCheckedChange = onToggleFloatingControls,
                        tag = "floating_controls_switch"
                    )
                }
            }

            // Quick Link to Recordings Library
            Button(
                onClick = onOpenRecordings,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("my_recordings_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "My Recordings", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardDark)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$recordingsCount videos",
                            color = NeonEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCustomSettingsDialog) {
        CustomSettingsDialog(
            initialConfig = config,
            codecReport = codecReport,
            onDismiss = { showCustomSettingsDialog = false },
            onSave = { updated ->
                onUpdateConfig(updated)
                showCustomSettingsDialog = false
            }
        )
    }
}

@Composable
fun HeaderSection(
    onOpenRecordings: () -> Unit,
    recordingsCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NeonEmerald)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GAMECAPTURE LITE",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "Low-Overhead PUBG Hardware Recorder",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(CardDark)
                .clickable { onOpenRecordings() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Recordings",
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$recordingsCount",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PresetSummaryBanner(config: RecordingConfig) {
    val resText = config.resolution.label
    val fpsText = config.fps.label
    val brText = config.bitrate.label
    val presetName = config.preset.label

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(SurfaceDark, CardDark)
                )
            )
            .border(1.dp, CardDark, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ACTIVE PRESET",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$resText / $fpsText / $brText",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonEmerald.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = presetName.uppercase(),
                    color = NeonEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun StartRecordingButton(
    onClick: () -> Unit,
    gamingMode: Boolean
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = CrimsonRecord,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("start_recording_button")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "START RECORDING",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                if (gamingMode) {
                    Text(
                        text = "Gaming Mode Armed • Zero CPU Bitmaps",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveRecordingHUD(
    recordingStatus: RecordingStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val durationMs = when (recordingStatus) {
        is RecordingStatus.Recording -> recordingStatus.durationMs
        is RecordingStatus.Paused -> recordingStatus.durationMs
        else -> 0L
    }
    val isPaused = recordingStatus is RecordingStatus.Paused
    val timerText = MediaStoreHelper.formatDuration(durationMs)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                colors = listOf(CrimsonRecord, WarningAmber)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isPaused) WarningAmber else CrimsonRecord)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPaused) "RECORDING PAUSED" else "RECORDING ACTIVE",
                    color = if (isPaused) WarningAmber else CrimsonRecord,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = timerText,
                color = TextWhite,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = if (isPaused) onResume else onPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) NeonEmerald else CardDark,
                        contentColor = if (isPaused) Color.Black else TextWhite
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("hud_pause_resume_button")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isPaused) "Resume" else "Pause", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonRecord,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("hud_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Stop & Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HardwareStatusCard(codecReport: CodecCapabilityReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = NeonEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hardware Encoder Engine",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            val encoderName = codecReport.h264HardwareEncoder ?: "MediaCodec Native (H.264)"
            Text(
                text = "Encoder: $encoderName",
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Zero-copy Surface pipeline • Direct MP4 Muxer",
                color = NeonEmerald,
                fontSize = 11.sp
            )

            codecReport.recommendation?.let { rec ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = rec,
                        color = WarningAmber,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PresetCardsGrid(
    selectedPreset: PresetType,
    onSelectPreset: (PresetType) -> Unit,
    onOpenCustom: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            PresetType.PERFORMANCE to "720p • 60 FPS • 8 Mbps (Lowest PUBG overhead)",
            PresetType.BALANCED to "1080p • 60 FPS • 12 Mbps (Standard high quality)",
            PresetType.HIGH_QUALITY to "1080p • 60 FPS • 16 Mbps (Maximum sharpness)"
        ).forEach { (preset, subtitle) ->
            val isSelected = selectedPreset == preset
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPreset(preset) }
                    .testTag("preset_${preset.name}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) NeonEmerald.copy(alpha = 0.12f) else CardDark
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(NeonEmerald, ElectricCyan))
                ) else null,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = preset.label,
                            color = if (isSelected) NeonEmerald else TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Custom Preset Card
        val isCustom = selectedPreset == PresetType.CUSTOM
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onSelectPreset(PresetType.CUSTOM)
                    onOpenCustom()
                }
                .testTag("preset_CUSTOM"),
            colors = CardDefaults.cardColors(
                containerColor = if (isCustom) ElectricCyan.copy(alpha = 0.12f) else CardDark
            ),
            border = if (isCustom) CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(ElectricCyan, NeonEmerald))
            ) else null,
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Custom Mode",
                        color = if (isCustom) ElectricCyan else TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Configure custom resolution, FPS, and bitrate",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configure",
                    tint = if (isCustom) ElectricCyan else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AudioSourceSelector(
    selectedSource: AudioSourceOption,
    supportsInternal: Boolean,
    onSelectSource: (AudioSourceOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AudioOptionCard(
            option = AudioSourceOption.INTERNAL,
            selected = selectedSource == AudioSourceOption.INTERNAL,
            icon = Icons.Default.Headphones,
            title = "Internal Game Audio",
            description = if (supportsInternal) "Clean PUBG sound without room noise (Android 10+)" else "Requires Android 10+",
            enabled = supportsInternal,
            onClick = { onSelectSource(AudioSourceOption.INTERNAL) }
        )

        AudioOptionCard(
            option = AudioSourceOption.MICROPHONE,
            selected = selectedSource == AudioSourceOption.MICROPHONE,
            icon = Icons.Default.Mic,
            title = "Microphone Only",
            description = "Player voice commentary and team chat",
            enabled = true,
            onClick = { onSelectSource(AudioSourceOption.MICROPHONE) }
        )

        AudioOptionCard(
            option = AudioSourceOption.BOTH,
            selected = selectedSource == AudioSourceOption.BOTH,
            icon = Icons.Default.Headphones,
            title = "Internal Audio + Microphone",
            description = "PUBG in-game sounds combined with player voice",
            enabled = supportsInternal,
            onClick = { onSelectSource(AudioSourceOption.BOTH) }
        )

        AudioOptionCard(
            option = AudioSourceOption.MUTE,
            selected = selectedSource == AudioSourceOption.MUTE,
            icon = Icons.Default.VolumeOff,
            title = "Mute (Video Only)",
            description = "No audio track for absolute lowest recording overhead",
            enabled = true,
            onClick = { onSelectSource(AudioSourceOption.MUTE) }
        )
    }
}

@Composable
fun AudioOptionCard(
    option: AudioSourceOption,
    selected: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .testTag("audio_${option.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) NeonEmerald.copy(alpha = 0.12f) else CardDark
        ),
        border = if (selected) CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(NeonEmerald, ElectricCyan))
        ) else null,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) NeonEmerald.copy(alpha = 0.2f) else SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) NeonEmerald else (if (enabled) TextSecondary else TextMuted),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (selected) NeonEmerald else (if (enabled) TextWhite else TextMuted),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = if (enabled) TextSecondary else TextMuted,
                    fontSize = 11.sp
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = NeonEmerald,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = NeonEmerald,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CardDark
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
