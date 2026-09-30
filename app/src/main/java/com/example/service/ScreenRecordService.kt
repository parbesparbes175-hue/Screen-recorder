package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.AudioSourceOption
import com.example.model.RecordingConfig
import com.example.model.RecordingStatus
import com.example.recorder.ScreenRecordEngine
import com.example.storage.MediaStoreHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenRecordService : Service() {

    companion object {
        private const val TAG = "ScreenRecordService"
        const val NOTIFICATION_ID = 4099
        const val CHANNEL_ID = "gamecapture_lite_channel"

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        // Global status flow accessible by UI
        private val _recordingState = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
        val recordingState: StateFlow<RecordingStatus> = _recordingState.asStateFlow()

        var currentConfig: RecordingConfig = RecordingConfig()

        fun isCurrentlyRecording(): Boolean {
            val s = _recordingState.value
            return s is RecordingStatus.Recording || s is RecordingStatus.Paused
        }
    }

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var recordEngine: ScreenRecordEngine? = null
    private var floatingControlManager: FloatingControlManager? = null
    private var isPaused = false

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordService = this@ScreenRecordService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != 0 && resultData != null) {
                    startRecordingInternal(resultCode, resultData)
                } else {
                    Log.e(TAG, "Invalid projection intent or result code")
                    _recordingState.value = RecordingStatus.Error("Media projection permission missing")
                    stopSelf()
                }
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecordingInternal(resultCode: Int, resultData: Intent) {
        // Start foreground immediately
        val initialNotification = buildNotification("Recording started...", isPaused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (currentConfig.audioSource != AudioSourceOption.MUTE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, initialNotification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                _recordingState.value = RecordingStatus.Error("Failed to obtain MediaProjection")
                stopSelf()
                return
            }
            mediaProjection = projection

            recordEngine = ScreenRecordEngine(
                context = this,
                mediaProjection = projection,
                config = currentConfig,
                onStatusChanged = { status ->
                    _recordingState.value = status
                    handleStatusUpdate(status)
                }
            )

            val started = recordEngine?.start() == true
            if (started) {
                if (currentConfig.floatingControlsEnabled) {
                    floatingControlManager = FloatingControlManager(
                        context = this,
                        onPauseResumeClicked = {
                            if (isPaused) resumeRecording() else pauseRecording()
                        },
                        onStopClicked = {
                            stopRecording()
                        }
                    )
                    floatingControlManager?.show()
                }
            } else {
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ScreenRecordService: ${e.message}", e)
            _recordingState.value = RecordingStatus.Error("Recording error: ${e.localizedMessage}")
            stopSelf()
        }
    }

    private fun handleStatusUpdate(status: RecordingStatus) {
        when (status) {
            is RecordingStatus.Recording -> {
                val formatted = MediaStoreHelper.formatDuration(status.durationMs)
                updateNotification("Recording PUBG • $formatted", isPaused = false)
                floatingControlManager?.updateTimer(status.durationMs)
                floatingControlManager?.setPaused(false)
            }
            is RecordingStatus.Paused -> {
                val formatted = MediaStoreHelper.formatDuration(status.durationMs)
                updateNotification("Paused • $formatted", isPaused = true)
                floatingControlManager?.setPaused(true)
            }
            is RecordingStatus.Stopped -> {
                floatingControlManager?.hide()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            is RecordingStatus.Error -> {
                floatingControlManager?.hide()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {}
        }
    }

    private fun pauseRecording() {
        isPaused = true
        recordEngine?.pause()
    }

    private fun resumeRecording() {
        isPaused = false
        recordEngine?.resume()
    }

    private fun stopRecording() {
        floatingControlManager?.hide()
        val uri = recordEngine?.stop()
        _recordingState.value = RecordingStatus.Stopped(uri, "GameCapture.mp4")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(contentText: String, isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause/Resume action
        val pauseResumeActionIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 1, pauseResumeActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseResumeTitle = if (isPaused) "Resume" else "Pause"

        // Stop action
        val stopActionIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GameCapture Lite")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, pauseResumeTitle, pauseResumePendingIntent)
            .addAction(android.R.drawable.checkbox_off_background, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(contentText: String, isPaused: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText, isPaused))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GameCapture Active Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording controls and status while PUBG Mobile is active."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        floatingControlManager?.hide()
        recordEngine?.stop()
        mediaProjection?.stop()
        super.onDestroy()
    }
}
