package com.rashed.ai.live

import android.util.Base64
import android.util.Log
import com.rashed.ai.command.CommandHandler
import com.rashed.ai.security.AuthTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Core manager for Gemini Live bidirectional WebSocket session.
 * Handles audio streaming, throttled video frames, interruption, tool calling, and session lifecycle.
 */
class GeminiLiveManager(
    private val tokenProvider: AuthTokenProvider,
    private val audioPlayer: LiveAudioPlayer,
    private val commandHandler: CommandHandler
) {

    companion object {
        private const val TAG = "GeminiLiveManager"
        private const val WS_HOST = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        private const val MAX_SESSION_DURATION_SECONDS = 900L // 15 minutes limit check
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for WebSocket
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var sessionTimerJob: Job? = null
    private var isIntentionalClose = false
    private var reconnectAttempts = 0

    private val _state = MutableStateFlow(LiveSessionState())
    val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LiveEvent> = _events.asSharedFlow()

    fun connect() {
        if (_state.value.status == SessionStatus.Connecting || _state.value.status == SessionStatus.Listening || _state.value.status == SessionStatus.Speaking) {
            Log.d(TAG, "Session already active or connecting")
            return
        }

        isIntentionalClose = false
        _state.value = _state.value.copy(
            status = SessionStatus.Connecting,
            errorMessage = null,
            isReconnecting = reconnectAttempts > 0
        )

        scope.launch {
            try {
                val token = tokenProvider.getEphemeralToken()
                val url = "$WS_HOST?key=$token"
                val request = Request.Builder().url(url).build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket connected. Sending setup...")
                        reconnectAttempts = 0
                        sendSetupMessage(webSocket)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleServerMessage(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closing: $code / $reason")
                        webSocket.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed: $code / $reason")
                        handleDisconnect(reason.ifBlank { "Session ended" })
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket failure: ${t.message}", t)
                        handleFailure(t)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Connection initiation failed: ${e.message}", e)
                _state.value = _state.value.copy(
                    status = SessionStatus.Error,
                    errorMessage = e.message ?: "কানেকশন শুরু করা যায়নি"
                )
                _events.tryEmit(LiveEvent.Error(e.message ?: "Failed to connect", e))
            }
        }
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupObj = JSONObject().apply {
                val setup = JSONObject().apply {
                    put("model", LIVE_MODEL)
                    val genConfig = JSONObject().apply {
                        val modalities = JSONArray().apply { put("AUDIO") }
                        put("responseModalities", modalities)
                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuilt = JSONObject().apply {
                                    put("voiceName", "Aoede")
                                }
                                put("prebuiltVoiceConfig", prebuilt)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", genConfig)

                    val systemInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            val part = JSONObject().apply {
                                put("text", "You are Rashed AI, a friendly AI companion and Android assistant. " +
                                        "The user may speak Bangla, Banglish, English, Hindi, or mixed languages. " +
                                        "Always respond naturally in the language the user is currently using. " +
                                        "If the user speaks Bangla, prioritize natural Bangla. " +
                                        "Keep spoken responses concise unless the user requests detail. " +
                                        "Never claim an action succeeded unless the application verified it. " +
                                        "When an action cannot be performed because of Android, app, permission, or API limitations, clearly explain the limitation. " +
                                        "When a tool is available, use the tool instead of pretending. " +
                                        "After completing an action, provide a short spoken confirmation and remain ready for the next command. " +
                                        "Never claim to see camera content unless an actual camera frame has been received.")
                            }
                            put(part)
                        }
                        put("parts", parts)
                    }
                    put("systemInstruction", systemInstruction)

                    // Tools declaration
                    val tools = JSONArray().apply {
                        val tool = JSONObject().apply {
                            put("functionDeclarations", buildToolDeclarations())
                        }
                        put(tool)
                    }
                    put("tools", tools)
                }
                put("setup", setup)
            }

            ws.send(setupObj.toString())
            Log.d(TAG, "Setup message transmitted.")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating setup message: ${e.message}", e)
        }
    }

    private fun buildToolDeclarations(): JSONArray {
        val list = JSONArray()

        // open_app
        list.put(JSONObject().apply {
            put("name", "open_app")
            put("description", "Opens an Android application installed on the device (e.g., YouTube, WhatsApp, Settings, Chrome, Camera)")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("app_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The name of the app to launch (e.g. YouTube, WhatsApp)")
                    })
                })
                put("required", JSONArray().apply { put("app_name") })
            })
        })

        // search_app
        list.put(JSONObject().apply {
            put("name", "search_app")
            put("description", "Searches for videos, content or topics inside an app (e.g. YouTube search)")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("app_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "App to search inside, default YouTube")
                    })
                    put("query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The search query (e.g. Ronaldo, funny cat videos)")
                    })
                })
                put("required", JSONArray().apply { put("query") })
            })
        })

        // whatsapp_message
        list.put(JSONObject().apply {
            put("name", "whatsapp_message")
            put("description", "Prepares or sends a WhatsApp message to a contact")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contact_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Contact name")
                    })
                    put("message", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Message text")
                    })
                    put("confirmed", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True if user already explicitly confirmed to send")
                    })
                })
                put("required", JSONArray().apply {
                    put("contact_name")
                    put("message")
                })
            })
        })

        // click_element
        list.put(JSONObject().apply {
            put("name", "click_element")
            put("description", "Clicks on an on-screen element or text using Accessibility")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("target_text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The visible label or button text to click")
                    })
                })
                put("required", JSONArray().apply { put("target_text") })
            })
        })

        // type_text
        list.put(JSONObject().apply {
            put("name", "type_text")
            put("description", "Types text into the active editable input field")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Text to type")
                    })
                })
                put("required", JSONArray().apply { put("text") })
            })
        })

        // scroll_screen
        list.put(JSONObject().apply {
            put("name", "scroll_screen")
            put("description", "Scrolls the active screen up or down")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("direction", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Direction: 'down' or 'up'")
                    })
                })
            })
        })

        // open_settings
        list.put(JSONObject().apply {
            put("name", "open_settings")
            put("description", "Opens device system settings like wifi, bluetooth, display, sound, or accessibility")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("setting_type", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Setting type: wifi, bluetooth, accessibility, sound, display, general")
                    })
                })
            })
        })

        // get_battery
        list.put(JSONObject().apply {
            put("name", "get_battery")
            put("description", "Retrieves actual battery level and charging status")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
            })
        })

        // get_network_status
        list.put(JSONObject().apply {
            put("name", "get_network_status")
            put("description", "Checks current Wi-Fi or cellular network connectivity status")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
            })
        })

        // get_storage
        list.put(JSONObject().apply {
            put("name", "get_storage")
            put("description", "Gets actual available storage space on device")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
            })
        })

        // control_media
        list.put(JSONObject().apply {
            put("name", "control_media")
            put("description", "Controls media playback (play, pause, next, prev)")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "play, pause, next, prev, stop")
                    })
                })
                put("required", JSONArray().apply { put("action") })
            })
        })

        // toggle_camera
        list.put(JSONObject().apply {
            put("name", "toggle_camera")
            put("description", "Turns camera on or off for visual conversation")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("enable", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True to turn on camera, false to turn off")
                    })
                })
                put("required", JSONArray().apply { put("enable") })
            })
        })

        return list
    }

    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)

            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup complete confirmed by server.")
                _state.value = _state.value.copy(
                    status = SessionStatus.Listening,
                    isReconnecting = false
                )
                _events.tryEmit(LiveEvent.Connected)
                _events.tryEmit(LiveEvent.Listening)
                startSessionTimer()
                return
            }

            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Model speech interrupted by server.")
                    audioPlayer.interrupt()
                    _state.value = _state.value.copy(
                        status = SessionStatus.Listening,
                        isModelSpeaking = false
                    )
                    _events.tryEmit(LiveEvent.Interrupted)
                    _events.tryEmit(LiveEvent.Listening)
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")

                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio part
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val dataBase64 = inlineData.optString("data")
                                if (dataBase64.isNotBlank()) {
                                    val pcmBytes = Base64.decode(dataBase64, Base64.NO_WRAP)
                                    audioPlayer.playChunk(pcmBytes)
                                    _state.value = _state.value.copy(
                                        status = SessionStatus.Speaking,
                                        isModelSpeaking = true
                                    )
                                    _events.tryEmit(LiveEvent.Speaking)
                                    _events.tryEmit(LiveEvent.AudioReceived(pcmBytes))
                                }
                            }

                            // Text transcript part
                            if (part.has("text")) {
                                val transcript = part.getString("text")
                                _state.value = _state.value.copy(
                                    lastModelTranscript = transcript
                                )
                                _events.tryEmit(LiveEvent.OutputTranscription(transcript))
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    _state.value = _state.value.copy(
                        status = SessionStatus.Listening,
                        isModelSpeaking = false
                    )
                    _events.tryEmit(LiveEvent.Listening)
                }
            }

            if (json.has("toolCall")) {
                val toolCall = json.getJSONObject("toolCall")
                handleToolCall(toolCall)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling server message: ${e.message}", e)
        }
    }

    private fun handleToolCall(toolCall: JSONObject) {
        val functionCalls = toolCall.optJSONArray("functionCalls") ?: return
        for (i in 0 until functionCalls.length()) {
            val call = functionCalls.getJSONObject(i)
            val name = call.optString("name")
            val callId = call.optString("id")
            val argsObj = call.optJSONObject("args")
            val argsMap = mutableMapOf<String, Any?>()

            argsObj?.keys()?.forEach { key ->
                argsMap[key] = argsObj.get(key)
            }

            _state.value = _state.value.copy(
                status = SessionStatus.Executing,
                lastActionReport = "Executing $name..."
            )
            _events.tryEmit(LiveEvent.ToolCallReceived(callId, name, argsMap))

            scope.launch {
                val result = commandHandler.executeTool(name, argsMap)
                _state.value = _state.value.copy(
                    lastActionReport = result.message
                )
                _events.tryEmit(LiveEvent.ActionCompleted(name, result.message, result.isSuccess))

                // Send tool response back to Gemini Live WebSocket
                sendToolResponse(callId, name, result.message, result.status.name)

                // CRITICAL ARCHITECTURE RULE: After executing an action, return to Listening
                _state.value = _state.value.copy(
                    status = SessionStatus.Listening
                )
                _events.tryEmit(LiveEvent.Listening)
            }
        }
    }

    private fun sendToolResponse(callId: String, name: String, message: String, status: String) {
        val ws = webSocket ?: return
        try {
            val responseObj = JSONObject().apply {
                val toolResponse = JSONObject().apply {
                    val functionResponses = JSONArray().apply {
                        val funcResp = JSONObject().apply {
                            put("id", callId)
                            val response = JSONObject().apply {
                                val output = JSONObject().apply {
                                    put("result", message)
                                    put("status", status)
                                }
                                put("output", output)
                            }
                            put("response", response)
                        }
                        put(funcResp)
                    }
                    put("functionResponses", functionResponses)
                }
                put("toolResponse", toolResponse)
            }

            ws.send(responseObj.toString())
            Log.d(TAG, "Tool response sent for callId: $callId, function: $name")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send tool response: ${e.message}", e)
        }
    }

    /**
     * Sends realtime PCM16 16kHz audio chunk to Gemini Live.
     */
    fun sendAudio(pcmChunk: ByteArray) {
        val ws = webSocket ?: return
        if (_state.value.status == SessionStatus.Disconnected || _state.value.status == SessionStatus.Connecting) return

        try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val msg = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        val chunk = JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        }
                        put(chunk)
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            ws.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio chunk: ${e.message}")
        }
    }

    /**
     * Sends a throttled JPEG video frame to Gemini Live.
     */
    fun sendVideoFrame(jpegBase64: String) {
        val ws = webSocket ?: return
        if (_state.value.status == SessionStatus.Disconnected) return

        try {
            val msg = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        val chunk = JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", jpegBase64)
                        }
                        put(chunk)
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            ws.send(msg.toString())
            Log.d(TAG, "Sent video frame to Gemini Live")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send video frame: ${e.message}")
        }
    }

    /**
     * User voice interruption: stops model audio playback immediately and sends client turn.
     */
    fun interrupt() {
        audioPlayer.interrupt()
        _state.value = _state.value.copy(
            status = SessionStatus.Listening,
            isModelSpeaking = false
        )
        _events.tryEmit(LiveEvent.Interrupted)
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = scope.launch {
            var seconds = 0L
            while (isActive && _state.value.isSessionActive) {
                delay(1000L)
                seconds++
                _state.value = _state.value.copy(sessionDurationSeconds = seconds)

                // Session limit renewal safeguard
                if (seconds >= MAX_SESSION_DURATION_SECONDS) {
                    Log.i(TAG, "Session approaching duration limit. Initiating renewal...")
                    reconnect()
                    break
                }
            }
        }
    }

    fun reconnect() {
        reconnectAttempts++
        disconnectInternal(false)
        scope.launch {
            delay(1000L)
            connect()
        }
    }

    private fun handleDisconnect(reason: String) {
        sessionTimerJob?.cancel()
        audioPlayer.interrupt()
        _state.value = _state.value.copy(
            status = SessionStatus.Disconnected,
            isModelSpeaking = false,
            isMicrophoneActive = false,
            isCameraActive = false
        )
        _events.tryEmit(LiveEvent.Disconnected(reason))

        if (!isIntentionalClose && reconnectAttempts < 3) {
            Log.i(TAG, "Attempting auto-reconnection (attempt #${reconnectAttempts + 1})...")
            reconnect()
        }
    }

    private fun handleFailure(t: Throwable) {
        sessionTimerJob?.cancel()
        audioPlayer.interrupt()
        _state.value = _state.value.copy(
            status = SessionStatus.Error,
            errorMessage = "বস, কানেকশন বিচ্ছিন্ন হয়ে গেছে: ${t.message}"
        )
        _events.tryEmit(LiveEvent.Error("Connection failure: ${t.message}", t))

        if (!isIntentionalClose && reconnectAttempts < 3) {
            reconnect()
        }
    }

    private fun disconnectInternal(intentional: Boolean) {
        isIntentionalClose = intentional
        sessionTimerJob?.cancel()
        audioPlayer.interrupt()
        try {
            webSocket?.close(1000, "User requested disconnect")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing websocket: ${e.message}")
        }
        webSocket = null
    }

    fun disconnect() {
        reconnectAttempts = 0
        disconnectInternal(true)
        _state.value = _state.value.copy(
            status = SessionStatus.Disconnected,
            isMicrophoneActive = false,
            isCameraActive = false,
            isModelSpeaking = false,
            sessionDurationSeconds = 0
        )
    }

    fun updateMicrophoneActive(active: Boolean) {
        _state.value = _state.value.copy(isMicrophoneActive = active)
    }

    fun updateCameraActive(active: Boolean) {
        _state.value = _state.value.copy(isCameraActive = active)
    }

    fun updateAudioLevels(inputLevel: Float, outputLevel: Float) {
        _state.value = _state.value.copy(
            audioInputLevel = inputLevel,
            audioOutputLevel = outputLevel
        )
    }
}
