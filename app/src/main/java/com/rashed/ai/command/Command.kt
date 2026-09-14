package com.rashed.ai.command

sealed class Command {
    data class OpenApp(val appName: String, val packageName: String? = null) : Command()
    data class SearchApp(val appName: String, val query: String) : Command()
    data class WhatsAppMessage(
        val contactName: String,
        val message: String,
        val confirmed: Boolean = false
    ) : Command()
    data class ClickText(val text: String) : Command()
    data class Scroll(val direction: String = "down") : Command()
    data class TypeText(val text: String) : Command()
    data class OpenSettings(val settingType: String = "general") : Command()
    object GetBattery : Command()
    object GetNetworkStatus : Command()
    object GetStorage : Command()
    data class ControlMedia(val action: String) : Command()
    data class ToggleCamera(val enable: Boolean) : Command()
}
