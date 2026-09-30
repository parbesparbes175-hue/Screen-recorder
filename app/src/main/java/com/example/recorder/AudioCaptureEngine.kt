package com.example.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.example.model.AudioSourceOption
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class AudioCaptureEngine(
    private val context: Context,
    private val audioOption: AudioSourceOption,
    private val mediaProjection: MediaProjection?,
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 2,
    private val bitRate: Int = 128000
) {
    companion object {
        private const val TAG = "AudioCaptureEngine"
        private const val TIMEOUT_USEC = 10000L
    }

    private var internalRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var audioCodec: MediaCodec? = null

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var workerThread: Thread? = null

    private var audioTrackIndex = -1
    private var isMuxerStarted = false

    var onAudioTrackConfigured: ((MediaFormat) -> Unit)? = null
    var onAudioDataEncoded: ((Int, ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun prepare(): Boolean {
        if (audioOption == AudioSourceOption.MUTE) {
            return true
        }

        val channelConfig = if (channelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize * 2, 8192)

        // 1. Prepare Internal AudioRecord if selected
        if (audioOption == AudioSourceOption.INTERNAL || audioOption == AudioSourceOption.BOTH) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                try {
                    val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()

                    internalRecord = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(playbackConfig)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .build()

                    if (internalRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.w(TAG, "Internal AudioRecord failed to initialize")
                        internalRecord?.release()
                        internalRecord = null
                        onError?.invoke("Internal audio not supported by this game or restricted by OS")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error configuring internal audio: ${e.message}", e)
                    internalRecord = null
                    onError?.invoke("Internal audio playback capture failed: ${e.localizedMessage}")
                }
            } else {
                Log.w(TAG, "Internal audio capture requires Android 10+ (API 29)")
                onError?.invoke("Internal audio capture requires Android 10+")
            }
        }

        // 2. Prepare Microphone AudioRecord if selected
        if (audioOption == AudioSourceOption.MICROPHONE || audioOption == AudioSourceOption.BOTH) {
            try {
                micRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (micRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "Microphone AudioRecord failed to initialize")
                    micRecord?.release()
                    micRecord = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing mic: ${e.message}", e)
                micRecord = null
            }
        }

        // If no audio source was successfully initialized
        if (internalRecord == null && micRecord == null) {
            Log.w(TAG, "No audio recording source available.")
            return false
        }

        // 3. Prepare AAC MediaCodec Encoder
        try {
            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            audioCodec = codec
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start AAC encoder: ${e.message}", e)
            cleanup()
            return false
        }

        return true
    }

    fun start() {
        if (audioOption == AudioSourceOption.MUTE || isRunning.get()) return

        try {
            internalRecord?.startRecording()
            micRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
        }

        isRunning.set(true)
        isPaused.set(false)

        workerThread = Thread({
            audioLoop()
        }, "GameCapture-AudioThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    private fun audioLoop() {
        val codec = audioCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val readSize = 2048
        val intBuffer = ByteArray(readSize)
        val micBuffer = ByteArray(readSize)
        val mixedBuffer = ByteArray(readSize)

        var presentationTimeUs = 0L
        val bytesPerSec = sampleRate * channelCount * 2

        while (isRunning.get()) {
            if (isPaused.get()) {
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            var bytesRead = 0
            val hasInternal = internalRecord != null
            val hasMic = micRecord != null

            if (hasInternal && hasMic) {
                val r1 = internalRecord?.read(intBuffer, 0, readSize) ?: 0
                val r2 = micRecord?.read(micBuffer, 0, readSize) ?: 0
                bytesRead = maxOf(r1, r2)
                if (bytesRead > 0) {
                    mixPcmBuffers(intBuffer, r1, micBuffer, r2, mixedBuffer, bytesRead)
                }
            } else if (hasInternal) {
                bytesRead = internalRecord?.read(mixedBuffer, 0, readSize) ?: 0
            } else if (hasMic) {
                bytesRead = micRecord?.read(mixedBuffer, 0, readSize) ?: 0
            }

            if (bytesRead > 0) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_USEC)
                if (inputIndex >= 0) {
                    val inputBuf = codec.getInputBuffer(inputIndex)
                    if (inputBuf != null) {
                        inputBuf.clear()
                        inputBuf.put(mixedBuffer, 0, bytesRead)
                        presentationTimeUs += (bytesRead.toLong() * 1_000_000L) / bytesPerSec
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            bytesRead,
                            presentationTimeUs,
                            0
                        )
                    }
                }
            }

            drainEncoder(codec, bufferInfo, false)
        }

        // Drain remaining
        drainEncoder(codec, bufferInfo, true)
    }

    private fun drainEncoder(codec: MediaCodec, bufferInfo: MediaCodec.BufferInfo, endOfStream: Boolean) {
        if (endOfStream) {
            val inputIndex = codec.dequeueInputBuffer(TIMEOUT_USEC)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        }

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                onAudioTrackConfigured?.invoke(newFormat)
            } else if (outputIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outputIndex)
                if (outBuf != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (bufferInfo.size != 0) {
                        onAudioDataEncoded?.invoke(audioTrackIndex, outBuf, bufferInfo)
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    /**
     * Fast 16-bit PCM linear mixing with clamping to prevent audio clipping.
     */
    private fun mixPcmBuffers(
        buf1: ByteArray, len1: Int,
        buf2: ByteArray, len2: Int,
        out: ByteArray, outLen: Int
    ) {
        val samples = outLen / 2
        for (i in 0 until samples) {
            val idx = i * 2
            val s1: Int = if (idx + 1 < len1) {
                (buf1[idx].toInt() and 0xFF) or (buf1[idx + 1].toInt() shl 8)
            } else {
                0
            }

            val s2: Int = if (idx + 1 < len2) {
                (buf2[idx].toInt() and 0xFF) or (buf2[idx + 1].toInt() shl 8)
            } else {
                0
            }

            val mixed = (s1 + s2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[idx] = (mixed and 0xFF).toByte()
            out[idx + 1] = ((mixed shr 8) and 0xFF).toByte()
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            workerThread?.join(500)
        } catch (_: InterruptedException) {}
        cleanup()
    }

    private fun cleanup() {
        try {
            internalRecord?.stop()
            internalRecord?.release()
        } catch (_: Exception) {}
        internalRecord = null

        try {
            micRecord?.stop()
            micRecord?.release()
        } catch (_: Exception) {}
        micRecord = null

        try {
            audioCodec?.stop()
            audioCodec?.release()
        } catch (_: Exception) {}
        audioCodec = null
    }
}
