package com.rashed.ai

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.rashed.ai.audio.AudioCaptureManager
import com.rashed.ai.audio.AudioPlaybackManager
import com.rashed.ai.command.CommandHandler
import com.rashed.ai.live.GeminiLiveManager
import com.rashed.ai.live.LiveAudioPlayer
import com.rashed.ai.permissions.PermissionManager
import com.rashed.ai.security.AuthTokenProvider
import com.rashed.ai.security.BackendAuthTokenProvider
import com.rashed.ai.security.LocalDevAuthTokenProvider

/**
 * Main application class for Rashed AI.
 * Hosts core singletons to ensure Live session and foreground voice execution persist across Activities.
 */
class RashedApplication : Application() {

    companion object {
        private const val TAG = "RashedApplication"
        const val PREFS_NAME = "rashed_ai_prefs"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_VOICE_NAME = "voice_name"
        const val KEY_AUTO_RECONNECT = "auto_reconnect"

        var instance: RashedApplication? = null
            private set
    }

    lateinit var prefs: SharedPreferences
        private set

    lateinit var audioPlaybackManager: AudioPlaybackManager
        private set

    lateinit var liveAudioPlayer: LiveAudioPlayer
        private set

    lateinit var commandHandler: CommandHandler
        private set

    lateinit var tokenProvider: AuthTokenProvider
        private set

    lateinit var geminiLiveManager: GeminiLiveManager
        private set

    lateinit var audioCaptureManager: AudioCaptureManager
        private set

    lateinit var permissionManager: PermissionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initializeDependencies()
        Log.d(TAG, "RashedApplication initialized.")
    }

    fun initializeDependencies() {
        audioPlaybackManager = AudioPlaybackManager()
        audioPlaybackManager.initialize()

        liveAudioPlayer = LiveAudioPlayer(audioPlaybackManager)

        commandHandler = CommandHandler(this) { enableCamera ->
            // Camera toggle callback
            Log.d(TAG, "Camera toggle requested via tool: $enableCamera")
        }

        tokenProvider = createTokenProvider()

        geminiLiveManager = GeminiLiveManager(
            tokenProvider = tokenProvider,
            audioPlayer = liveAudioPlayer,
            commandHandler = commandHandler
        )

        permissionManager = PermissionManager(this)

        audioCaptureManager = AudioCaptureManager(
            context = this,
            onAudioChunkCaptured = { pcmChunk ->
                geminiLiveManager.sendAudio(pcmChunk)
                geminiLiveManager.updateAudioLevels(
                    audioCaptureManager.audioInputLevel.value,
                    audioPlaybackManager.audioOutputLevel.value
                )
            },
            onSpeechDetected = {
                // When user speech is detected while model is speaking, trigger voice interruption
                if (geminiLiveManager.state.value.isModelSpeaking) {
                    Log.d(TAG, "User speech detected during model output. Triggering interruption...")
                    geminiLiveManager.interrupt()
                }
            }
        )
    }

    fun updateBackendUrl(url: String) {
        prefs.edit().putString(KEY_BACKEND_URL, url).apply()
        tokenProvider = createTokenProvider()
        // Reinitialize live manager with new provider
        geminiLiveManager = GeminiLiveManager(
            tokenProvider = tokenProvider,
            audioPlayer = liveAudioPlayer,
            commandHandler = commandHandler
        )
    }

    private fun createTokenProvider(): AuthTokenProvider {
        val configuredUrl = prefs.getString(KEY_BACKEND_URL, "") ?: ""
        return if (configuredUrl.isNotBlank()) {
            BackendAuthTokenProvider(configuredUrl)
        } else {
            // Development fallback with BuildConfig injected key
            LocalDevAuthTokenProvider(BuildConfig.GEMINI_API_KEY)
        }
    }
}
