package com.rashed.ai.camera

import androidx.camera.core.CameraSelector

enum class CameraLens {
    BACK,
    FRONT
}

data class CameraState(
    val isStreaming: Boolean = false,
    val hasPermission: Boolean = false,
    val lens: CameraLens = CameraLens.BACK,
    val framesSent: Long = 0,
    val lastFrameTimestamp: Long = 0,
    val errorMessage: String? = null
) {
    val cameraSelector: CameraSelector
        get() = if (lens == CameraLens.FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
}
