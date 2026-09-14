package com.rashed.ai.live

/**
 * Events dispatched across the Gemini Live lifecycle.
 */
sealed interface LiveEvent {
    object Connected : LiveEvent
    data class Disconnected(val reason: String) : LiveEvent
    object Listening : LiveEvent
    object Thinking : LiveEvent
    object Speaking : LiveEvent
    data class AudioReceived(val pcmData: ByteArray) : LiveEvent
    data class InputTranscription(val text: String) : LiveEvent
    data class OutputTranscription(val text: String) : LiveEvent
    data class Error(val message: String, val cause: Throwable? = null) : LiveEvent
    object Interrupted : LiveEvent
    data class ToolCallReceived(
        val callId: String,
        val functionName: String,
        val arguments: Map<String, Any?>
    ) : LiveEvent
    data class ActionCompleted(
        val functionName: String,
        val resultMessage: String,
        val success: Boolean
    ) : LiveEvent
}
