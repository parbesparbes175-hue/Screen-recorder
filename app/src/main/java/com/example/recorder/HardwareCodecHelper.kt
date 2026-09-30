package com.example.recorder

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.model.CodecCapabilityReport
import com.example.model.RecordingConfig
import com.example.model.VideoFps
import com.example.model.VideoResolution

object HardwareCodecHelper {
    private const val TAG = "HardwareCodecHelper"

    /**
     * Finds the best hardware-accelerated video encoder for the specified MIME type.
     * Falls back to any available encoder if strictly required.
     */
    fun findEncoder(mimeType: String, preferHardware: Boolean = true): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val allEncoders = codecList.codecInfos.filter { it.isEncoder }

        val matchingEncoders = allEncoders.filter { info ->
            try {
                info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            } catch (e: Exception) {
                false
            }
        }

        if (preferHardware) {
            val hwEncoder = matchingEncoders.firstOrNull { isHardwareCodec(it) }
            if (hwEncoder != null) {
                Log.d(TAG, "Selected hardware encoder for $mimeType: ${hwEncoder.name}")
                return hwEncoder
            }
        }

        val fallback = matchingEncoders.firstOrNull()
        Log.w(TAG, "Hardware encoder not found for $mimeType, fallback: ${fallback?.name}")
        return fallback
    }

    /**
     * Checks if a codec is hardware-accelerated without software emulation.
     */
    fun isHardwareCodec(codecInfo: MediaCodecInfo): Boolean {
        if (!codecInfo.isEncoder) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            codecInfo.isHardwareAccelerated && !codecInfo.isSoftwareOnly
        } else {
            val name = codecInfo.name.lowercase()
            !name.startsWith("omx.google.") &&
                    !name.startsWith("c2.android.") &&
                    !name.contains(".sw.") &&
                    !name.contains("google")
        }
    }

    /**
     * Evaluates device capabilities and builds a comprehensive report for the user.
     */
    fun inspectCapabilities(context: Context): CodecCapabilityReport {
        val h264Encoder = findEncoder(MediaFormat.MIMETYPE_VIDEO_AVC, preferHardware = true)
        val hevcEncoder = findEncoder(MediaFormat.MIMETYPE_VIDEO_HEVC, preferHardware = true)

        var supports1080p60 = false
        var supports720p60 = false
        var maxFps1080 = 30.0

        h264Encoder?.let { info ->
            try {
                val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                val videoCaps = caps.videoCapabilities
                if (videoCaps != null) {
                    supports720p60 = videoCaps.areSizeAndRateSupported(1280, 720, 60.0)
                    supports1080p60 = videoCaps.areSizeAndRateSupported(1920, 1080, 60.0)

                    val fpsRange = videoCaps.getSupportedFrameRatesFor(1920, 1080)
                    maxFps1080 = fpsRange?.upper?.toDouble() ?: 30.0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying video capabilities: ${e.message}")
            }
        }

        val supportsInternalAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        var recommendation: String? = null
        if (!supports1080p60) {
            recommendation = if (supports720p60) {
                "1080p @ 60 FPS is not hardware-supported. Performance Mode (720p @ 60 FPS) is strongly recommended for PUBG Mobile."
            } else {
                "60 FPS is not supported by the hardware encoder. Recommended setting: 1080p or 720p @ 30 FPS."
            }
        }

        return CodecCapabilityReport(
            h264HardwareEncoder = h264Encoder?.name?.takeIf { isHardwareCodec(h264Encoder) },
            hevcHardwareEncoder = hevcEncoder?.name?.takeIf { isHardwareCodec(hevcEncoder) },
            supports1080p60 = supports1080p60,
            supports720p60 = supports720p60,
            maxFpsAt1080p = maxFps1080,
            supportsInternalAudio = supportsInternalAudio,
            recommendation = recommendation
        )
    }

    /**
     * Calculates the recording video dimensions based on screen resolution and user selection.
     * Aligns dimensions to 16-byte boundary for maximum H.264 encoder compatibility.
     */
    fun calculateVideoDimensions(context: Context, resolution: VideoResolution): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val isLandscape = screenWidth > screenHeight
        val longSide = maxOf(screenWidth, screenHeight)
        val shortSide = minOf(screenWidth, screenHeight)

        val targetShort = resolution.standardHeight
        val targetLong = ((longSide.toFloat() / shortSide.toFloat()) * targetShort).toInt()

        // Align to multiples of 16 (macroblock size in AVC/H.264)
        val alignedShort = (targetShort / 16) * 16
        val alignedLong = (targetLong / 16) * 16

        return if (isLandscape) {
            Pair(alignedLong, alignedShort)
        } else {
            Pair(alignedShort, alignedLong)
        }
    }

    /**
     * Validates whether a specific RecordingConfig is viable on the hardware encoder,
     * and returns an adjusted config if needed.
     */
    fun getOptimizedConfig(config: RecordingConfig, report: CodecCapabilityReport): RecordingConfig {
        if (config.fps == VideoFps.FPS_60 && !report.supports1080p60 && config.resolution == VideoResolution.RES_1080P) {
            // Downgrade to 720p 60fps or 1080p 30fps
            return if (report.supports720p60) {
                config.copy(resolution = VideoResolution.RES_720P)
            } else {
                config.copy(fps = VideoFps.FPS_30)
            }
        }
        return config
    }
}
