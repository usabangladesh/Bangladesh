package com.rashed.ai.command

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.rashed.ai.accessibility.AccessibilityCommandExecutor

/**
 * Handles interpretation, execution, and verification of structured tool calls
 * requested by Gemini Live.
 */
class CommandHandler(
    private val context: Context,
    private val onCameraToggleRequested: (Boolean) -> Unit = {}
) {

    companion object {
        private const val TAG = "CommandHandler"
    }

    private val accessibilityExecutor = AccessibilityCommandExecutor(context)

    fun executeTool(functionName: String, args: Map<String, Any?>): ActionResult {
        Log.d(TAG, "Executing tool: $functionName with args: $args")
        return when (functionName) {
            "open_app" -> {
                val appName = args["app_name"]?.toString() ?: ""
                if (appName.isBlank()) {
                    ActionResult(ActionResultStatus.FAILED, "অ্যাপের নাম পাওয়া যায়নি।")
                } else {
                    accessibilityExecutor.openSupportedApp(appName)
                }
            }
            "search_app" -> {
                val appName = args["app_name"]?.toString() ?: "YouTube"
                val query = args["query"]?.toString() ?: ""
                if (query.isBlank()) {
                    ActionResult(ActionResultStatus.FAILED, "সার্চ কুয়েরি ফাঁকা রাখা যাবে না।")
                } else {
                    accessibilityExecutor.searchInApp(appName, query)
                }
            }
            "whatsapp_message" -> {
                val contactName = args["contact_name"]?.toString() ?: "বন্ধু"
                val message = args["message"]?.toString() ?: ""
                val confirmed = (args["confirmed"] as? Boolean) ?: false
                accessibilityExecutor.prepareWhatsAppMessage(contactName, message, confirmed)
            }
            "click_element" -> {
                val text = args["target_text"]?.toString() ?: ""
                if (text.isBlank()) {
                    ActionResult(ActionResultStatus.FAILED, "ক্লিক করার এলিমেন্টের নাম নেই।")
                } else {
                    accessibilityExecutor.clickText(text)
                }
            }
            "type_text" -> {
                val text = args["text"]?.toString() ?: ""
                accessibilityExecutor.typeText(text)
            }
            "scroll_screen" -> {
                val direction = args["direction"]?.toString() ?: "down"
                accessibilityExecutor.scroll(direction)
            }
            "open_settings" -> {
                openDeviceSettings(args["setting_type"]?.toString())
            }
            "get_battery" -> {
                getBatteryStatus()
            }
            "get_network_status" -> {
                getNetworkStatus()
            }
            "get_storage" -> {
                getStorageStatus()
            }
            "control_media" -> {
                controlMedia(args["action"]?.toString() ?: "play_pause")
            }
            "toggle_camera" -> {
                val enable = (args["enable"] as? Boolean) ?: true
                onCameraToggleRequested(enable)
                ActionResult(
                    ActionResultStatus.SUCCESS,
                    if (enable) "ক্যামেরা চালু করা হয়েছে বস।" else "ক্যামেরা বন্ধ করা হয়েছে বস。"
                )
            }
            else -> {
                Log.w(TAG, "Unknown tool call requested: $functionName")
                ActionResult(
                    ActionResultStatus.UNSUPPORTED,
                    "বস, '$functionName' কমান্ডটি বর্তমানে সমর্থিত নয়।"
                )
            }
        }
    }

    private fun openDeviceSettings(settingType: String?): ActionResult {
        val action = when (settingType?.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult(ActionResultStatus.SUCCESS, "ডিভাইস সেটিংস খোলা হয়েছে বস।")
        } catch (e: Exception) {
            ActionResult(ActionResultStatus.FAILED, "সেটিংস খুলতে ব্যর্থ: ${e.message}")
        }
    }

    private fun getBatteryStatus(): ActionResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging == true

        return if (level >= 0) {
            val chargingText = if (isCharging) "চার্জ হচ্ছে" else "চার্জে নেই"
            ActionResult(
                ActionResultStatus.SUCCESS,
                "ব্যাটারি চার্জ আছে $level% এবং বর্তমানে $chargingText বস।",
                mapOf("level" to level, "charging" to isCharging)
            )
        } else {
            ActionResult(ActionResultStatus.FAILED, "ব্যাটারির তথ্য সংগ্রহ করা যায়নি।")
        }
    }

    private fun getNetworkStatus(): ActionResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)

        return if (caps != null) {
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val typeStr = when {
                isWifi -> "Wi-Fi"
                isCellular -> "মোবাইল ডাটা"
                else -> "ইন্টারনেট"
            }
            ActionResult(
                ActionResultStatus.SUCCESS,
                "বর্তমানে $typeStr কানেকশন সক্রিয় আছে বস।",
                mapOf("connected" to true, "type" to typeStr)
            )
        } else {
            ActionResult(
                ActionResultStatus.SUCCESS,
                "বর্তমানে কোনো ইন্টারনেট কানেকশন পাওয়া যায়নি বস।",
                mapOf("connected" to false)
            )
        }
    }

    private fun getStorageStatus(): ActionResult {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            val bytesTotal = stat.blockCountLong * stat.blockSizeLong
            val gbAvailable = bytesAvailable / (1024 * 1024 * 1024)
            val gbTotal = bytesTotal / (1024 * 1024 * 1024)

            ActionResult(
                ActionResultStatus.SUCCESS,
                "ডিভাইসে মোট $gbTotal GB-এর মধ্যে $gbAvailable GB খালি রয়েছে বস।",
                mapOf("available_gb" to gbAvailable, "total_gb" to gbTotal)
            )
        } catch (e: Exception) {
            ActionResult(ActionResultStatus.FAILED, "স্টোরেজ হিসেব করা যায়নি: ${e.message}")
        }
    }

    private fun controlMedia(action: String): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            return ActionResult(ActionResultStatus.FAILED, "অডিও ম্যানেজার পাওয়া যায়নি।")
        }

        val keyCode = when (action.lowercase()) {
            "play", "pause", "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev", "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        return try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            ActionResult(ActionResultStatus.SUCCESS, "মিডিয়া কমান্ড '$action' কার্যকর হয়েছে বস।")
        } catch (e: Exception) {
            ActionResult(ActionResultStatus.FAILED, "মিডিয়া কমান্ড পাঠাতে ব্যর্থ: ${e.message}")
        }
    }
}
