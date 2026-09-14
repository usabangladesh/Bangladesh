package com.rashed.ai.accessibility

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.rashed.ai.command.ActionResult
import com.rashed.ai.command.ActionResultStatus

/**
 * Executes structured automation commands across Android apps and UI controls.
 * Uses explicit intents first, official deep links second, and AccessibilityService where needed.
 */
class AccessibilityCommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "AccessibilityExecutor"

        val COMMON_APPS = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "settings" to "com.android.settings",
            "camera" to "com.sec.android.app.camera",
            "maps" to "com.google.android.apps.maps",
            "play store" to "com.android.vending",
            "gmail" to "com.google.android.gm",
            "messages" to "com.google.android.apps.messaging",
            "clock" to "com.sec.android.app.clockpackage",
            "gallery" to "com.sec.android.gallery3d"
        )
    }

    /**
     * Launches an app using Android Intent.
     */
    fun openSupportedApp(appName: String): ActionResult {
        val normalizedName = appName.trim().lowercase()
        val packageName = COMMON_APPS[normalizedName] ?: findPackageByName(normalizedName)

        if (packageName == null) {
            return ActionResult(
                status = ActionResultStatus.FAILED,
                message = "বস, '$appName' অ্যাপটি ডিভাইসে খুঁজে পাওয়া যায়নি।"
            )
        }

        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
                Log.d(TAG, "Successfully launched app: $appName ($packageName)")
                ActionResult(
                    status = ActionResultStatus.SUCCESS,
                    message = "$appName খুলে গেছে বস।",
                    details = mapOf("package" to packageName)
                )
            } else {
                ActionResult(
                    status = ActionResultStatus.FAILED,
                    message = "বস, $appName ওপেন করার উপযুক্ত Intent পাওয়া যায়নি।"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app $appName: ${e.message}", e)
            ActionResult(
                status = ActionResultStatus.FAILED,
                message = "বস, $appName খুলতে গিয়ে সমস্যা হয়েছে: ${e.message}"
            )
        }
    }

    /**
     * Searches content inside an app (e.g. YouTube search).
     */
    fun searchInApp(appName: String, query: String): ActionResult {
        val normalized = appName.trim().lowercase()
        if (normalized.contains("youtube")) {
            return try {
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                ActionResult(
                    status = ActionResultStatus.SUCCESS,
                    message = "YouTube-এ '$query' সার্চ করা হয়েছে বস।"
                )
            } catch (e: Exception) {
                // Fallback to web search uri
                try {
                    val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
                    val webIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(webIntent)
                    ActionResult(
                        status = ActionResultStatus.SUCCESS,
                        message = "YouTube-এ '$query' দেখানো হচ্ছে বস।"
                    )
                } catch (ex: Exception) {
                    ActionResult(
                        status = ActionResultStatus.FAILED,
                        message = "বস, YouTube সার্চ করতে সমস্যা হয়েছে: ${ex.message}"
                    )
                }
            }
        } else {
            // General web search intent
            return try {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                ActionResult(
                    status = ActionResultStatus.SUCCESS,
                    message = "'$query' সার্চ করা হয়েছে বস।"
                )
            } catch (e: Exception) {
                ActionResult(
                    status = ActionResultStatus.FAILED,
                    message = "সার্চ সম্পন্ন করা যায়নি: ${e.message}"
                )
            }
        }
    }

    /**
     * Prepares a WhatsApp message to a contact.
     * Sending requires confirmation.
     */
    fun prepareWhatsAppMessage(contactName: String, messageText: String, confirmed: Boolean): ActionResult {
        if (!confirmed) {
            return ActionResult(
                status = ActionResultStatus.SUCCESS,
                message = "$contactName-কে '$messageText' মেসেজটি পাঠাতে চান? হ্যাঁ বললে পাঠিয়ে দেব বস।",
                details = mapOf("awaiting_confirmation" to true, "contact" to contactName, "text" to messageText)
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(messageText))
            }
            context.startActivity(intent)
            ActionResult(
                status = ActionResultStatus.SUCCESS,
                message = "WhatsApp মেসেজ পাঠানো হয়েছে বস।"
            )
        } catch (e: Exception) {
            ActionResult(
                status = ActionResultStatus.FAILED,
                message = "WhatsApp-এ মেসেজ পাঠাতে সমস্যা হয়েছে: ${e.message}"
            )
        }
    }

    /**
     * Clicks an on-screen element matching the text using AccessibilityService.
     */
    fun clickText(targetText: String): ActionResult {
        val service = RashedAccessibilityService.instance
            ?: return ActionResult(
                status = ActionResultStatus.PERMISSION_REQUIRED,
                message = "বস, স্ক্রিনে ক্লিক করার জন্য Accessibility Service চালু করা প্রয়োজন।"
            )

        val success = service.clickByText(targetText)
        return if (success) {
            ActionResult(
                status = ActionResultStatus.SUCCESS,
                message = "'$targetText'-এ ক্লিক করা হয়েছে বস।"
            )
        } else {
            ActionResult(
                status = ActionResultStatus.FAILED,
                message = "বস, স্ক্রিনে '$targetText' খুঁজে পাওয়া যায়নি।"
            )
        }
    }

    /**
     * Types text into the currently active editable field.
     */
    fun typeText(text: String): ActionResult {
        val service = RashedAccessibilityService.instance
            ?: return ActionResult(
                status = ActionResultStatus.PERMISSION_REQUIRED,
                message = "বস, লেখার জন্য Accessibility Service চালু করা প্রয়োজন।"
            )

        val success = service.typeIntoFocusedField(text)
        return if (success) {
            ActionResult(
                status = ActionResultStatus.SUCCESS,
                message = "'$text' লেখা হয়েছে বস।"
            )
        } else {
            ActionResult(
                status = ActionResultStatus.FAILED,
                message = "বস, লেখার জন্য কোনো ইনপুট ফিল্ড পাওয়া যায়নি।"
            )
        }
    }

    /**
     * Scrolls the active screen.
     */
    fun scroll(direction: String): ActionResult {
        val service = RashedAccessibilityService.instance
            ?: return ActionResult(
                status = ActionResultStatus.PERMISSION_REQUIRED,
                message = "বস, স্ক্রল করার জন্য Accessibility Service চালু করা প্রয়োজন।"
            )

        val forward = direction.lowercase() != "up"
        val success = service.performScroll(forward)
        return if (success) {
            ActionResult(
                status = ActionResultStatus.SUCCESS,
                message = "স্ক্রিন স্ক্রল করা হয়েছে বস।"
            )
        } else {
            ActionResult(
                status = ActionResultStatus.FAILED,
                message = "বস, স্ক্রল করার মতো কোনো স্ক্রলযোগ্য অংশ পাওয়া যায়নি।"
            )
        }
    }

    /**
     * Reads visible text on the active screen.
     */
    fun findVisibleText(): ActionResult {
        val service = RashedAccessibilityService.instance
            ?: return ActionResult(
                status = ActionResultStatus.PERMISSION_REQUIRED,
                message = "বস, স্ক্রিনের লেখা পড়ার জন্য Accessibility Service অন করা দরকার।"
            )

        val texts = service.extractVisibleText()
        return ActionResult(
            status = ActionResultStatus.SUCCESS,
            message = "স্ক্রিনে ${texts.size}টি টেক্সট অংশ দেখা যাচ্ছে।",
            details = mapOf("texts" to texts)
        )
    }

    private fun findPackageByName(name: String): String? {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == name || label.contains(name)) {
                return app.packageName
            }
        }
        return null
    }
}
