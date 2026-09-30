package com.example.model

import android.net.Uri

enum class PresetType(val label: String, val description: String) {
    PERFORMANCE("Performance", "720p • 60 FPS • 8 Mbps (Lowest overhead)"),
    BALANCED("Balanced", "1080p • 60 FPS • 12 Mbps (Recommended)"),
    HIGH_QUALITY("High Quality", "1080p • 60 FPS • 16 Mbps (Sharpest detail)"),
    CUSTOM("Custom", "User defined resolution, FPS, and bitrate")
}

enum class VideoResolution(val label: String, val standardHeight: Int) {
    RES_720P("720p", 720),
    RES_1080P("1080p", 1080)
}

enum class VideoFps(val value: Int, val label: String) {
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS")
}

enum class VideoBitrate(val bps: Int, val label: String) {
    MBPS_4(4_000_000, "4 Mbps"),
    MBPS_6(6_000_000, "6 Mbps"),
    MBPS_8(8_000_000, "8 Mbps"),
    MBPS_12(12_000_000, "12 Mbps"),
    MBPS_16(16_000_000, "16 Mbps"),
    MBPS_20(20_000_000, "20 Mbps")
}

enum class VideoCodecType(val mimeType: String, val label: String) {
    H264("video/avc", "H.264 / AVC (Most Compatible)"),
    HEVC("video/hevc", "H.265 / HEVC (High Efficiency)")
}

enum class AudioSourceOption(val label: String, val description: String) {
    INTERNAL("Internal Audio", "Game audio directly (Android 10+)"),
    MICROPHONE("Microphone", "Voice and external sound"),
    BOTH("Both", "Internal game audio + Microphone"),
    MUTE("Mute", "Video only without audio")
}

data class RecordingConfig(
    val preset: PresetType = PresetType.BALANCED,
    val resolution: VideoResolution = VideoResolution.RES_1080P,
    val fps: VideoFps = VideoFps.FPS_60,
    val bitrate: VideoBitrate = VideoBitrate.MBPS_12,
    val codec: VideoCodecType = VideoCodecType.H264,
    val audioSource: AudioSourceOption = AudioSourceOption.INTERNAL,
    val countdownEnabled: Boolean = true,
    val floatingControlsEnabled: Boolean = true,
    val gamingModeEnabled: Boolean = true
) {
    companion object {
        fun fromPreset(preset: PresetType): RecordingConfig = when (preset) {
            PresetType.PERFORMANCE -> RecordingConfig(
                preset = PresetType.PERFORMANCE,
                resolution = VideoResolution.RES_720P,
                fps = VideoFps.FPS_60,
                bitrate = VideoBitrate.MBPS_8,
                codec = VideoCodecType.H264
            )
            PresetType.BALANCED -> RecordingConfig(
                preset = PresetType.BALANCED,
                resolution = VideoResolution.RES_1080P,
                fps = VideoFps.FPS_60,
                bitrate = VideoBitrate.MBPS_12,
                codec = VideoCodecType.H264
            )
            PresetType.HIGH_QUALITY -> RecordingConfig(
                preset = PresetType.HIGH_QUALITY,
                resolution = VideoResolution.RES_1080P,
                fps = VideoFps.FPS_60,
                bitrate = VideoBitrate.MBPS_16,
                codec = VideoCodecType.H264
            )
            PresetType.CUSTOM -> RecordingConfig(
                preset = PresetType.CUSTOM,
                resolution = VideoResolution.RES_1080P,
                fps = VideoFps.FPS_60,
                bitrate = VideoBitrate.MBPS_12,
                codec = VideoCodecType.H264
            )
        }
    }
}

sealed class RecordingStatus {
    object Idle : RecordingStatus()
    data class Countdown(val secondsRemaining: Int) : RecordingStatus()
    data class Recording(val durationMs: Long, val filePath: String? = null) : RecordingStatus()
    data class Paused(val durationMs: Long) : RecordingStatus()
    data class Stopped(val fileUri: Uri?, val fileName: String?) : RecordingStatus()
    data class Error(val message: String) : RecordingStatus()
}

data class RecordingItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val width: Int = 0,
    val height: Int = 0
)

data class CodecCapabilityReport(
    val h264HardwareEncoder: String?,
    val hevcHardwareEncoder: String?,
    val supports1080p60: Boolean,
    val supports720p60: Boolean,
    val maxFpsAt1080p: Double,
    val supportsInternalAudio: Boolean,
    val recommendation: String?
)
