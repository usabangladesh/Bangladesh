package com.rashed.ai.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.rashed.ai.accessibility.RashedAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionState(
    val hasRecordAudio: Boolean = false,
    val hasCamera: Boolean = false,
    val hasPostNotifications: Boolean = false,
    val hasAccessibility: Boolean = false
) {
    val isCoreReady: Boolean
        get() = hasRecordAudio
}

/**
 * Checks and monitors real runtime permissions and accessibility service states.
 */
class PermissionManager(private val context: Context) {

    private val _permissionState = MutableStateFlow(checkPermissions())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    fun refresh() {
        _permissionState.value = checkPermissions()
    }

    fun checkPermissions(): PermissionState {
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val accessibilityActive = RashedAccessibilityService.instance != null || isAccessibilityServiceEnabled()

        return PermissionState(
            hasRecordAudio = audioGranted,
            hasCamera = cameraGranted,
            hasPostNotifications = notificationGranted,
            hasAccessibility = accessibilityActive
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "${context.packageName}/${RashedAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(expectedService) || enabledServices.contains(context.packageName)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
