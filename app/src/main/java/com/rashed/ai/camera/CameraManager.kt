package com.rashed.ai.camera

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Manages CameraX lifecycle, preview display, and throttled frame extraction for Gemini Live visual input.
 */
class CameraManager(
    private val context: Context,
    private val onFrameCaptured: (jpegBase64: String) -> Unit
) {

    companion object {
        private const val TAG = "CameraManager"
    }

    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val frameProcessor = CameraFrameProcessor(
        frameIntervalMs = 1000L, // 1 frame per second
        maxDimension = 640,
        jpegQuality = 75
    ) { base64Frame ->
        _cameraState.value = _cameraState.value.copy(
            framesSent = _cameraState.value.framesSent + 1,
            lastFrameTimestamp = System.currentTimeMillis()
        )
        onFrameCaptured(base64Frame)
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView? = null
    ) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _cameraState.value = _cameraState.value.copy(
                hasPermission = false,
                isStreaming = false,
                errorMessage = "Camera permission not granted"
            )
            return
        }

        _cameraState.value = _cameraState.value.copy(hasPermission = true, errorMessage = null)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX: ${e.message}", e)
                _cameraState.value = _cameraState.value.copy(
                    isStreaming = false,
                    errorMessage = e.message
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?
    ) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val selector = _cameraState.value.cameraSelector

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(cameraExecutor) { imageProxy ->
                    frameProcessor.processImage(imageProxy)
                }
            }

        try {
            if (previewView != null) {
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
            } else {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    imageAnalysis
                )
            }

            _cameraState.value = _cameraState.value.copy(
                isStreaming = true,
                errorMessage = null
            )
            Log.d(TAG, "Camera started successfully with lens: ${_cameraState.value.lens}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases: ${e.message}", e)
            _cameraState.value = _cameraState.value.copy(
                isStreaming = false,
                errorMessage = "Failed to start camera: ${e.message}"
            )
        }
    }

    fun toggleLens(lifecycleOwner: LifecycleOwner, previewView: PreviewView?) {
        val newLens = if (_cameraState.value.lens == CameraLens.BACK) {
            CameraLens.FRONT
        } else {
            CameraLens.BACK
        }
        _cameraState.value = _cameraState.value.copy(lens = newLens)
        if (_cameraState.value.isStreaming) {
            bindCameraUseCases(lifecycleOwner, previewView)
        }
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            _cameraState.value = _cameraState.value.copy(isStreaming = false)
            Log.d(TAG, "Camera stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera: ${e.message}")
        }
    }

    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
