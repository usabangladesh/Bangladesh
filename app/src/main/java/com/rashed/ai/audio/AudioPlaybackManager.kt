package com.rashed.ai.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.abs

/**
 * Manages low-latency real-time PCM audio playback from Gemini Live.
 * Output format: PCM 16-bit, 24000 Hz, mono.
 */
class AudioPlaybackManager {

    companion object {
        private const val TAG = "AudioPlaybackManager"
        const val SAMPLE_RATE = 24000
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _audioOutputLevel = MutableStateFlow(0f)
    val audioOutputLevel: StateFlow<Float> = _audioOutputLevel.asStateFlow()

    @Synchronized
    fun initialize() {
        if (audioTrack != null) return

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            startPlaybackLoop()
            Log.d(TAG, "AudioTrack initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}", e)
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isActive) {
                try {
                    val chunk = audioQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (chunk != null && chunk.isNotEmpty()) {
                        _isPlaying.value = true
                        calculateAmplitude(chunk)
                        val track = audioTrack
                        if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                            track.write(chunk, 0, chunk.size)
                        }
                    } else {
                        if (audioQueue.isEmpty()) {
                            _isPlaying.value = false
                            _audioOutputLevel.value = 0f
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error during audio playback: ${e.message}")
                }
            }
        }
    }

    /**
     * Enqueue a raw PCM audio chunk for immediate streaming playback.
     */
    fun enqueueAudioChunk(pcmChunk: ByteArray) {
        if (audioTrack == null) {
            initialize()
        }
        audioQueue.offer(pcmChunk)
    }

    /**
     * Immediately interrupts and clears all playing and buffered audio.
     * Prevents old assistant voice from playing over fresh speech.
     */
    @Synchronized
    fun interrupt() {
        audioQueue.clear()
        _isPlaying.value = false
        _audioOutputLevel.value = 0f
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
            Log.d(TAG, "Audio playback interrupted and flushed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error interrupting playback: ${e.message}")
        }
    }

    private fun calculateAmplitude(chunk: ByteArray) {
        var sum = 0L
        val sampleCount = chunk.size / 2
        if (sampleCount == 0) return

        for (i in 0 until chunk.size step 2) {
            val low = chunk[i].toInt() and 0xFF
            val high = chunk[i + 1].toInt()
            val sample = (high shl 8) or low
            sum += abs(sample)
        }
        val avg = sum.toFloat() / sampleCount
        val normalized = (avg / 16384f).coerceIn(0f, 1f)
        _audioOutputLevel.value = normalized
    }

    @Synchronized
    fun release() {
        audioQueue.clear()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                    track.release()
                }
            }
            audioTrack = null
            _isPlaying.value = false
            _audioOutputLevel.value = 0f
            Log.d(TAG, "AudioTrack released.")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
        }
    }
}
