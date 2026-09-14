package com.rashed.ai.live

import com.rashed.ai.audio.AudioPlaybackManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface and wrapper for playing Gemini Live audio response streams.
 */
class LiveAudioPlayer(private val playbackManager: AudioPlaybackManager) {

    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val audioOutputLevel: StateFlow<Float> = playbackManager.audioOutputLevel

    fun playChunk(pcmChunk: ByteArray) {
        playbackManager.enqueueAudioChunk(pcmChunk)
    }

    fun interrupt() {
        playbackManager.interrupt()
    }

    fun stop() {
        playbackManager.interrupt()
    }

    fun release() {
        playbackManager.release()
    }
}
