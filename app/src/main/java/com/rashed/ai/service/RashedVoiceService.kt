package com.rashed.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rashed.ai.MainActivity
import com.rashed.ai.RashedApplication
import com.rashed.ai.live.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service for Rashed AI.
 * Ensures the voice assistant and Live session remain alive and responsive
 * even when MainActivity goes to the background (e.g., when launching YouTube or WhatsApp).
 */
class RashedVoiceService : Service() {

    companion object {
        private const val TAG = "RashedVoiceService"
        const val CHANNEL_ID = "rashed_ai_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.rashed.ai.action.START"
        const val ACTION_STOP = "com.rashed.ai.action.STOP"
        const val ACTION_PAUSE = "com.rashed.ai.action.PAUSE"
        const val ACTION_RESUME = "com.rashed.ai.action.RESUME"

        @Volatile
        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, RashedVoiceService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RashedVoiceService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        Log.d(TAG, "RashedVoiceService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Action STOP received in service")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                RashedApplication.instance?.geminiLiveManager?.interrupt()
                updateNotification("Assistant paused")
            }
            ACTION_RESUME -> {
                RashedApplication.instance?.geminiLiveManager?.connect()
                updateNotification("Resuming assistant...")
            }
            ACTION_START, null -> {
                startForegroundWithNotification()
                observeLiveState()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Rashed AI is active", "Listening for your commands...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeLiveState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            val liveManager = RashedApplication.instance?.geminiLiveManager ?: return@launch
            liveManager.state.collectLatest { state ->
                val statusText = when (state.status) {
                    SessionStatus.Listening -> "Listening..."
                    SessionStatus.Speaking -> "Speaking..."
                    SessionStatus.Thinking -> "Thinking..."
                    SessionStatus.Executing -> "Executing action..."
                    SessionStatus.CameraActive -> "Camera active"
                    SessionStatus.Connecting -> "Connecting to Gemini Live..."
                    SessionStatus.Disconnected -> "Disconnected"
                    SessionStatus.Error -> "Connection error"
                }
                updateNotification(statusText)
            }
        }
    }

    private fun updateNotification(statusText: String) {
        val notification = buildNotification("Rashed AI is active", statusText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RashedVoiceService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, RashedVoiceService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, RashedVoiceService::class.java).apply { action = ACTION_RESUME },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rashed AI Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live assistant connection and voice status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        stateObserverJob?.cancel()
        Log.d(TAG, "RashedVoiceService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
