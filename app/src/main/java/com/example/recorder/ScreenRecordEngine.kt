package com.example.recorder

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.example.model.AudioSourceOption
import com.example.model.RecordingConfig
import com.example.model.RecordingStatus
import com.example.storage.MediaStoreHelper
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecordEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val config: RecordingConfig,
    private val onStatusChanged: (RecordingStatus) -> Unit
) {
    companion object {
        private const val TAG = "ScreenRecordEngine"
        private const val TIMEOUT_USEC = 10000L
    }

    private var videoCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaMuxer: MediaMuxer? = null

    private var audioEngine: AudioCaptureEngine? = null

    private var fileUri: Uri? = null
    private var fileDescriptor: FileDescriptor? = null

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private val muxerLock = Any()

    private var encoderThread: Thread? = null
    private var timerThread: Thread? = null

    private var startTimeMs = 0L
    private var pausedDurationMs = 0L
    private var pauseStartTimeMs = 0L
    private var recordedDurationMs = 0L

    // Timestamp adjustment for pause/resume
    private var lastVideoPtsUs = 0L
    private var ptsOffsetUs = 0L
    private var isFirstFrame = true

    fun start(): Boolean {
        if (isRecording.get()) return true

        try {
            // 1. Create MediaStore file
            val (uri, fd) = MediaStoreHelper.createRecordingFile(context)
            if (uri == null || fd == null) {
                onStatusChanged(RecordingStatus.Error("Failed to create video file on device storage"))
                return false
            }
            fileUri = uri
            fileDescriptor = fd

            // 2. Setup Dimensions & Display Metrics
            val (width, height) = HardwareCodecHelper.calculateVideoDimensions(context, config.resolution)
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val screenDpi = metrics.densityDpi

            // 3. Configure Hardware Video MediaCodec
            val mimeType = config.codec.mimeType
            val hwEncoder = HardwareCodecHelper.findEncoder(mimeType, preferHardware = true)
            val codec = if (hwEncoder != null) {
                MediaCodec.createByCodecName(hwEncoder.name)
            } else {
                MediaCodec.createEncoderByType(mimeType)
            }

            val videoFormat = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate.bps)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps.value)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1s keyframe for fast seeking & stability
                setInteger(MediaFormat.KEY_CAPTURE_RATE, config.fps.value)
                setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / config.fps.value)
            }

            codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            videoCodec = codec
            inputSurface = surface

            // 4. Create MediaMuxer
            mediaMuxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 5. Setup Audio Engine if not muted
            if (config.audioSource != AudioSourceOption.MUTE) {
                val audio = AudioCaptureEngine(
                    context = context,
                    audioOption = config.audioSource,
                    mediaProjection = mediaProjection
                )

                audio.onAudioTrackConfigured = { format ->
                    synchronized(muxerLock) {
                        if (!muxerStarted && audioTrackIndex == -1) {
                            audioTrackIndex = mediaMuxer?.addTrack(format) ?: -1
                            checkStartMuxer()
                        }
                    }
                }

                audio.onAudioDataEncoded = { _, buffer, bufferInfo ->
                    synchronized(muxerLock) {
                        if (muxerStarted && audioTrackIndex != -1 && !isPaused.get()) {
                            try {
                                mediaMuxer?.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                            } catch (e: Exception) {
                                Log.w(TAG, "Audio write sample error: ${e.message}")
                            }
                        }
                    }
                }

                audio.onError = { errorMsg ->
                    Log.w(TAG, "Audio Engine error: $errorMsg")
                }

                if (audio.prepare()) {
                    audioEngine = audio
                }
            }

            // 6. Connect Virtual Display directly to MediaCodec input Surface
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "GameCapture-Display",
                width,
                height,
                screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            // 7. Start threads
            isRecording.set(true)
            isPaused.set(false)
            startTimeMs = System.currentTimeMillis()

            audioEngine?.start()

            encoderThread = Thread({
                videoDrainLoop()
            }, "GameCapture-VideoThread").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            timerThread = Thread({
                durationTimerLoop()
            }, "GameCapture-TimerThread").apply {
                start()
            }

            onStatusChanged(RecordingStatus.Recording(0, fileUri.toString()))
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen recording pipeline: ${e.message}", e)
            cleanup()
            onStatusChanged(RecordingStatus.Error("Failed to start encoder: ${e.localizedMessage}"))
            return false
        }
    }

    private fun checkStartMuxer() {
        val requiresAudio = audioEngine != null
        val audioReady = !requiresAudio || audioTrackIndex != -1
        val videoReady = videoTrackIndex != -1

        if (videoReady && audioReady && !muxerStarted) {
            try {
                mediaMuxer?.start()
                muxerStarted = true
                Log.d(TAG, "MediaMuxer started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "MediaMuxer start failed: ${e.message}", e)
            }
        }
    }

    fun pause() {
        if (!isRecording.get() || isPaused.get()) return
        isPaused.set(true)
        pauseStartTimeMs = System.currentTimeMillis()
        audioEngine?.pause()

        // Signal video encoder suspend
        videoCodec?.let { codec ->
            try {
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 1)
                }
                codec.setParameters(params)
            } catch (e: Exception) {
                Log.w(TAG, "Error setting codec suspend: ${e.message}")
            }
        }
        onStatusChanged(RecordingStatus.Paused(recordedDurationMs))
    }

    fun resume() {
        if (!isRecording.get() || !isPaused.get()) return
        val pauseDuration = System.currentTimeMillis() - pauseStartTimeMs
        pausedDurationMs += pauseDuration
        ptsOffsetUs += (pauseDuration * 1000L)

        isPaused.set(false)
        audioEngine?.resume()

        // Resume video encoder
        videoCodec?.let { codec ->
            try {
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0)
                }
                codec.setParameters(params)
            } catch (e: Exception) {
                Log.w(TAG, "Error resuming codec: ${e.message}")
            }
        }
        onStatusChanged(RecordingStatus.Recording(recordedDurationMs, fileUri.toString()))
    }

    private fun videoDrainLoop() {
        val codec = videoCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (isRecording.get()) {
            val outputIndex = try {
                codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            } catch (e: Exception) {
                Log.w(TAG, "Codec dequeue error: ${e.message}")
                break
            }

            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // No output available right now
                continue
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized(muxerLock) {
                    if (videoTrackIndex == -1) {
                        val newFormat = codec.outputFormat
                        videoTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                        checkStartMuxer()
                    }
                }
            } else if (outputIndex >= 0) {
                val encodedData = codec.getOutputBuffer(outputIndex)
                if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (bufferInfo.size != 0 && muxerStarted && !isPaused.get()) {
                        // Adjust timestamp so paused duration does not leave dead space
                        if (isFirstFrame) {
                            lastVideoPtsUs = bufferInfo.presentationTimeUs
                            isFirstFrame = false
                        }
                        val adjustedPts = maxOf(0L, bufferInfo.presentationTimeUs - ptsOffsetUs)
                        bufferInfo.presentationTimeUs = adjustedPts

                        synchronized(muxerLock) {
                            try {
                                mediaMuxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                            } catch (e: Exception) {
                                Log.w(TAG, "Video write sample error: ${e.message}")
                            }
                        }
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }
    }

    private fun durationTimerLoop() {
        while (isRecording.get()) {
            if (!isPaused.get()) {
                val elapsed = System.currentTimeMillis() - startTimeMs - pausedDurationMs
                recordedDurationMs = maxOf(0L, elapsed)
                onStatusChanged(RecordingStatus.Recording(recordedDurationMs, fileUri.toString()))
            }
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    fun stop(): Uri? {
        if (!isRecording.get()) return null
        isRecording.set(false)
        isPaused.set(false)

        try {
            timerThread?.interrupt()
            encoderThread?.join(500)
        } catch (_: Exception) {}

        audioEngine?.stop()

        try {
            videoCodec?.signalEndOfInputStream()
        } catch (_: Exception) {}

        cleanup()

        fileUri?.let { uri ->
            MediaStoreHelper.finalizeRecordingFile(context, uri)
            onStatusChanged(RecordingStatus.Stopped(uri, "GameCapture.mp4"))
        }

        return fileUri
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            inputSurface?.release()
        } catch (_: Exception) {}
        inputSurface = null

        try {
            videoCodec?.stop()
            videoCodec?.release()
        } catch (_: Exception) {}
        videoCodec = null

        synchronized(muxerLock) {
            try {
                if (muxerStarted) {
                    mediaMuxer?.stop()
                }
                mediaMuxer?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaMuxer: ${e.message}")
            }
            mediaMuxer = null
            muxerStarted = false
        }
    }
}
