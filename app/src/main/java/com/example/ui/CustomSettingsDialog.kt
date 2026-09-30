package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CodecCapabilityReport
import com.example.model.PresetType
import com.example.model.RecordingConfig
import com.example.model.VideoBitrate
import com.example.model.VideoCodecType
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.ui.theme.CardDark
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomSettingsDialog(
    initialConfig: RecordingConfig,
    codecReport: CodecCapabilityReport,
    onDismiss: () -> Unit,
    onSave: (RecordingConfig) -> Unit
) {
    var resolution by remember { mutableStateOf(initialConfig.resolution) }
    var fps by remember { mutableStateOf(initialConfig.fps) }
    var bitrate by remember { mutableStateOf(initialConfig.bitrate) }
    var codec by remember { mutableStateOf(initialConfig.codec) }

    // Real-time hardware capability validation
    val isUnsupported1080p60 = resolution == VideoResolution.RES_1080P &&
            fps == VideoFps.FPS_60 &&
            !codecReport.supports1080p60

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                text = "Custom Video Settings",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Resolution section
                Text(
                    text = "RESOLUTION",
                    color = NeonEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoResolution.values().forEach { res ->
                        val selected = res == resolution
                        ChipOption(
                            text = res.label,
                            selected = selected,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("res_${res.name}"),
                            onClick = { resolution = res }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Frame Rate section
                Text(
                    text = "FRAME RATE (FPS)",
                    color = NeonEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoFps.values().forEach { f ->
                        val selected = f == fps
                        ChipOption(
                            text = f.label,
                            selected = selected,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fps_${f.name}"),
                            onClick = { fps = f }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bitrate section
                Text(
                    text = "BITRATE",
                    color = NeonEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoBitrate.values().forEach { br ->
                        val selected = br == bitrate
                        ChipOption(
                            text = br.label,
                            selected = selected,
                            modifier = Modifier.testTag("bitrate_${br.name}"),
                            onClick = { bitrate = br }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Codec section
                Text(
                    text = "VIDEO ENCODER",
                    color = NeonEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hevcSupported = codecReport.hevcHardwareEncoder != null
                    VideoCodecType.values().forEach { c ->
                        val isHevc = c == VideoCodecType.HEVC
                        val enabled = !isHevc || hevcSupported
                        val selected = c == codec

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NeonEmerald.copy(alpha = 0.15f) else CardDark)
                                .clickable(enabled = enabled) {
                                    if (enabled) codec = c
                                }
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = c.label,
                                        color = if (enabled) (if (selected) NeonEmerald else TextWhite) else TextMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (isHevc && !hevcSupported) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(No HW Encoder)",
                                            color = WarningAmber,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Hardware warning if 1080p60 is not supported
                if (isUnsupported1080p60) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(WarningAmber.copy(alpha = 0.15f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Device Encoder Warning",
                                    color = WarningAmber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Your hardware encoder may struggle with 1080p 60 FPS in PUBG Mobile. 720p 60 FPS or 1080p 30 FPS is recommended to prevent dropped frames.",
                                    color = TextWhite,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialConfig.copy(
                            preset = PresetType.CUSTOM,
                            resolution = resolution,
                            fps = fps,
                            bitrate = bitrate,
                            codec = codec
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black),
                modifier = Modifier.testTag("save_custom_settings_button")
            ) {
                Text("Apply Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ChipOption(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) NeonEmerald else CardDark)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else TextWhite,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
