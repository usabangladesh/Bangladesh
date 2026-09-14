package com.rashed.ai.viewmodel

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.rashed.ai.RashedApplication
import com.rashed.ai.camera.CameraManager
import com.rashed.ai.camera.CameraState
import com.rashed.ai.live.LiveSessionState
import com.rashed.ai.live.SessionStatus
import com.rashed.ai.permissions.PermissionState
import com.rashed.ai.service.RashedVoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScreenDestination {
    MAIN,
    SETTINGS,
    PERMISSIONS,
    PROVIDER
}

class RashedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RashedApplication
    val liveManager = app.geminiLiveManager
    val permissionManager = app.permissionManager
    private val captureManager = app.audioCaptureManager

    val liveState: StateFlow<LiveSessionState> = liveManager.state
    val permissionState: StateFlow<PermissionState> = permissionManager.permissionState

    val cameraManager = CameraManager(application) { jpegBase64 ->
        liveManager.sendVideoFrame(jpegBase64)
    }
    val cameraState: StateFlow<CameraState> = cameraManager.cameraState

    private val _currentScreen = MutableStateFlow(ScreenDestination.MAIN)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    init {
        permissionManager.refresh()
    }

    fun navigateTo(screen: ScreenDestination) {
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        return if (_currentScreen.value != ScreenDestination.MAIN) {
            _currentScreen.value = ScreenDestination.MAIN
            true
        } else {
            false
        }
    }

    fun toggleLiveSession() {
        if (liveState.value.isSessionActive) {
            stopSession()
        } else {
            startSession()
        }
    }

    fun startSession() {
        permissionManager.refresh()
        if (!permissionState.value.hasRecordAudio) {
            _currentScreen.value = ScreenDestination.PERMISSIONS
            return
        }

        // Start Foreground Service
        RashedVoiceService.startService(getApplication())

        // Connect to Gemini Live
        liveManager.connect()

        // Start audio capture
        val captureSuccess = captureManager.startCapture()
        liveManager.updateMicrophoneActive(captureSuccess)
    }

    fun stopSession() {
        captureManager.stopCapture()
        liveManager.updateMicrophoneActive(false)
        cameraManager.stopCamera()
        liveManager.updateCameraActive(false)
        liveManager.disconnect()
        RashedVoiceService.stopService(getApplication())
    }

    fun toggleCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView? = null) {
        permissionManager.refresh()
        if (!permissionState.value.hasCamera) {
            _currentScreen.value = ScreenDestination.PERMISSIONS
            return
        }

        if (cameraState.value.isStreaming) {
            cameraManager.stopCamera()
            liveManager.updateCameraActive(false)
        } else {
            cameraManager.startCamera(lifecycleOwner, previewView)
            liveManager.updateCameraActive(true)
        }
    }

    fun switchCameraLens(lifecycleOwner: LifecycleOwner, previewView: PreviewView? = null) {
        cameraManager.toggleLens(lifecycleOwner, previewView)
    }

    fun interruptSpeech() {
        liveManager.interrupt()
    }

    fun refreshPermissions() {
        permissionManager.refresh()
    }

    fun openAppSettings() {
        permissionManager.openAppSettings()
    }

    fun openAccessibilitySettings() {
        permissionManager.openAccessibilitySettings()
    }

    fun updateBackendUrl(url: String) {
        app.updateBackendUrl(url)
    }

    override fun onCleared() {
        super.onCleared()
        // Note: We do not abruptly stop RashedVoiceService if foreground voice mode is preferred
    }
}
