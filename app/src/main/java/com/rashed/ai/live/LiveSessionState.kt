package com.rashed.ai.live

/**
 * Detailed connection and operational status of the Gemini Live session.
 */
enum class SessionStatus {
    Disconnected,
    Connecting,
    Listening,
    Thinking,
    Executing,
    Speaking,
    CameraActive,
    Error
}

/**
 * State representation for Rashed AI Live session.
 */
data class LiveSessionState(
    val status: SessionStatus = SessionStatus.Disconnected,
    val isMicrophoneActive: Boolean = false,
    val isCameraActive: Boolean = false,
    val isModelSpeaking: Boolean = false,
    val lastUserTranscript: String = "",
    val lastModelTranscript: String = "",
    val lastActionReport: String = "",
    val errorMessage: String? = null,
    val sessionDurationSeconds: Long = 0,
    val audioInputLevel: Float = 0f,
    val audioOutputLevel: Float = 0f,
    val isReconnecting: Boolean = false
) {
    val isSessionActive: Boolean
        get() = status != SessionStatus.Disconnected && status != SessionStatus.Error
}
