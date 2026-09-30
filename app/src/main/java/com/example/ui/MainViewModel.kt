package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AudioSourceOption
import com.example.model.CodecCapabilityReport
import com.example.model.PresetType
import com.example.model.RecordingConfig
import com.example.model.RecordingItem
import com.example.model.RecordingStatus
import com.example.recorder.HardwareCodecHelper
import com.example.service.ScreenRecordService
import com.example.storage.MediaStoreHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val _config = MutableStateFlow(RecordingConfig())
    val config: StateFlow<RecordingConfig> = _config.asStateFlow()

    private val _codecReport = MutableStateFlow<CodecCapabilityReport>(
        HardwareCodecHelper.inspectCapabilities(application)
    )
    val codecReport: StateFlow<CodecCapabilityReport> = _codecReport.asStateFlow()

    val recordingStatus: StateFlow<RecordingStatus> = ScreenRecordService.recordingState

    private val _localStatus = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    val localStatus: StateFlow<RecordingStatus> = _localStatus.asStateFlow()

    private val _recordingsList = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordingsList: StateFlow<List<RecordingItem>> = _recordingsList.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var countdownJob: Job? = null
    private var pendingProjectionData: Pair<Int, Intent>? = null

    init {
        // Automatically check if recommended preset needs adjustment based on hardware
        val report = _codecReport.value
        if (!report.supports1080p60 && report.supports720p60) {
            _config.value = RecordingConfig.fromPreset(PresetType.PERFORMANCE)
        }
        loadRecordings()
    }

    fun selectPreset(preset: PresetType) {
        if (preset == PresetType.CUSTOM) {
            _config.value = _config.value.copy(preset = PresetType.CUSTOM)
        } else {
            val base = RecordingConfig.fromPreset(preset)
            _config.value = base.copy(
                audioSource = _config.value.audioSource,
                countdownEnabled = _config.value.countdownEnabled,
                floatingControlsEnabled = _config.value.floatingControlsEnabled,
                gamingModeEnabled = _config.value.gamingModeEnabled
            )
        }
    }

    fun updateConfig(newConfig: RecordingConfig) {
        _config.value = newConfig
    }

    fun setAudioSource(audioSource: AudioSourceOption) {
        if (audioSource == AudioSourceOption.INTERNAL || audioSource == AudioSourceOption.BOTH) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                _userMessage.value = "Internal audio capture requires Android 10 or newer."
                return
            }
        }
        _config.value = _config.value.copy(audioSource = audioSource)
    }

    fun toggleGamingMode(enabled: Boolean) {
        _config.value = _config.value.copy(gamingModeEnabled = enabled)
    }

    fun toggleCountdown(enabled: Boolean) {
        _config.value = _config.value.copy(countdownEnabled = enabled)
    }

    fun toggleFloatingControls(enabled: Boolean) {
        _config.value = _config.value.copy(floatingControlsEnabled = enabled)
    }

    fun onProjectionPermissionGranted(resultCode: Int, data: Intent) {
        pendingProjectionData = Pair(resultCode, data)

        if (_config.value.countdownEnabled) {
            startCountdown()
        } else {
            launchRecordingService(resultCode, data)
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in 3 downTo 1) {
                _localStatus.value = RecordingStatus.Countdown(sec)
                vibrate(50)
                delay(1000)
            }
            vibrate(120)
            _localStatus.value = RecordingStatus.Idle
            pendingProjectionData?.let { (code, intent) ->
                launchRecordingService(code, intent)
            }
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        pendingProjectionData = null
        _localStatus.value = RecordingStatus.Idle
    }

    private fun launchRecordingService(resultCode: Int, data: Intent) {
        ScreenRecordService.currentConfig = _config.value
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pauseRecording() {
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeRecording() {
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopRecording() {
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        context.startService(intent)

        // Reload recordings when stopped
        viewModelScope.launch {
            delay(800)
            loadRecordings()
        }
    }

    fun loadRecordings() {
        // Do not query during active recording to keep CPU 0%
        if (ScreenRecordService.isCurrentlyRecording()) return

        viewModelScope.launch {
            val list = MediaStoreHelper.queryRecordings(context)
            _recordingsList.value = list
        }
    }

    fun deleteRecording(item: RecordingItem) {
        viewModelScope.launch {
            val success = MediaStoreHelper.deleteRecording(context, item.uri)
            if (success) {
                _recordingsList.value = _recordingsList.value.filter { it.id != item.id }
            } else {
                _userMessage.value = "Failed to delete file"
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    private fun vibrate(durationMs: Long) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }
}
