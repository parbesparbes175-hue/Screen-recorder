package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.model.AudioSourceOption
import com.example.model.RecordingStatus
import com.example.ui.CountdownOverlay
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.RecordingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppScreen {
    RECORDER,
    RECORDINGS_LIBRARY
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRecordings()
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.RECORDER) }

    val config by viewModel.config.collectAsState()
    val codecReport by viewModel.codecReport.collectAsState()
    val recordingStatus by viewModel.recordingStatus.collectAsState()
    val localStatus by viewModel.localStatus.collectAsState()
    val recordingsList by viewModel.recordingsList.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    // MediaProjection screen capture launcher
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            viewModel.onProjectionPermissionGranted(result.resultCode, result.data!!)
        } else {
            Toast.makeText(context, "Screen capture permission was denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Audio recording runtime permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Launch screen capture intent
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            Toast.makeText(context, "Microphone permission is required for audio commentary.", Toast.LENGTH_SHORT).show()
        }
    }

    // Notifications permission launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Start Recording initiator with permissions checks
    val initiateRecording = {
        // 1. Check Notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Check Floating window permission if requested
        if (config.floatingControlsEnabled && !Settings.canDrawOverlays(context)) {
            try {
                val overlayIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(overlayIntent)
                Toast.makeText(context, "Enable \"Display over other apps\" for PUBG floating controls.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
        }

        // 3. Check Microphone permission if needed
        val needsMic = config.audioSource == AudioSourceOption.MICROPHONE || config.audioSource == AudioSourceOption.BOTH
        if (needsMic && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            AppScreen.RECORDER -> {
                MainScreen(
                    config = config,
                    codecReport = codecReport,
                    recordingStatus = recordingStatus,
                    recordingsCount = recordingsList.size,
                    onStartRecordingClicked = { initiateRecording() },
                    onPauseClicked = { viewModel.pauseRecording() },
                    onResumeClicked = { viewModel.resumeRecording() },
                    onStopClicked = { viewModel.stopRecording() },
                    onSelectPreset = { viewModel.selectPreset(it) },
                    onUpdateConfig = { viewModel.updateConfig(it) },
                    onSelectAudioSource = { viewModel.setAudioSource(it) },
                    onToggleGamingMode = { viewModel.toggleGamingMode(it) },
                    onToggleCountdown = { viewModel.toggleCountdown(it) },
                    onToggleFloatingControls = { viewModel.toggleFloatingControls(it) },
                    onOpenRecordings = {
                        viewModel.loadRecordings()
                        currentScreen = AppScreen.RECORDINGS_LIBRARY
                    },
                    userMessage = userMessage,
                    onClearUserMessage = { viewModel.clearUserMessage() }
                )
            }
            AppScreen.RECORDINGS_LIBRARY -> {
                RecordingsScreen(
                    recordings = recordingsList,
                    onBack = { currentScreen = AppScreen.RECORDER },
                    onDelete = { viewModel.deleteRecording(it) }
                )
            }
        }

        // Active countdown overlay
        if (localStatus is RecordingStatus.Countdown) {
            val sec = (localStatus as RecordingStatus.Countdown).secondsRemaining
            CountdownOverlay(
                secondsRemaining = sec,
                onCancel = { viewModel.cancelCountdown() }
            )
        }
    }
}
