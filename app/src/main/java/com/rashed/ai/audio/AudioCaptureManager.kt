package com.rashed.ai.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Captures 16-bit PCM mono 16000Hz audio from the microphone using AudioRecord.
 * Provides continuous chunk streaming and voice activity detection for interruption.
 */
class AudioCaptureManager(
    private val context: Context,
    private val onAudioChunkCaptured: (ByteArray) -> Unit,
    private val onSpeechDetected: () -> Unit = {}
) {

    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = 1024 // 512 samples of 16-bit audio
        private const val SPEECH_THRESHOLD = 1800f
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioInputLevel = MutableStateFlow(0f)
    val audioInputLevel: StateFlow<Float> = _audioInputLevel.asStateFlow()

    private var consecutiveSpeechFrames = 0

    @SuppressLint("MissingPermission")
    fun startCapture(): Boolean {
        if (_isRecording.value) return true

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return false
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 2).coerceAtLeast(CHUNK_SIZE * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            startCaptureLoop()
            Log.d(TAG, "Audio capture started successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting audio capture: ${e.message}", e)
            _isRecording.value = false
            return false
        }
    }

    private fun startCaptureLoop() {
        recordingJob?.cancel()
        recordingJob = scope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ByteArray(CHUNK_SIZE)

            while (isActive && _isRecording.value) {
                val record = audioRecord ?: break
                val bytesRead = record.read(buffer, 0, buffer.size)

                if (bytesRead > 0) {
                    val pcmData = buffer.copyOf(bytesRead)
                    calculateInputLevel(pcmData)
                    onAudioChunkCaptured(pcmData)
                } else if (bytesRead < 0) {
                    Log.w(TAG, "AudioRecord read error code: $bytesRead")
                }
            }
        }
    }

    private fun calculateInputLevel(pcmData: ByteArray) {
        var sum = 0L
        val sampleCount = pcmData.size / 2
        if (sampleCount == 0) return

        for (i in 0 until pcmData.size step 2) {
            val low = pcmData[i].toInt() and 0xFF
            val high = pcmData[i + 1].toInt()
            val sample = (high shl 8) or low
            sum += abs(sample)
        }
        val avg = sum.toFloat() / sampleCount
        val normalized = (avg / 12000f).coerceIn(0f, 1f)
        _audioInputLevel.value = normalized

        // Detect user speech for voice interruption
        if (avg > SPEECH_THRESHOLD) {
            consecutiveSpeechFrames++
            if (consecutiveSpeechFrames >= 2) {
                onSpeechDetected()
            }
        } else {
            consecutiveSpeechFrames = 0
        }
    }

    fun stopCapture() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        _audioInputLevel.value = 0f

        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
            audioRecord = null
            Log.d(TAG, "Audio capture stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }
}
