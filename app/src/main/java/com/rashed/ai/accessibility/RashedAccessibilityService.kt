package com.rashed.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-authorized Accessibility Service for Rashed AI.
 * Enables UI inspection, control clicking, typing, and scrolling upon user voice commands.
 */
class RashedAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RashedAccessibility"

        @Volatile
        var instance: RashedAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.d(TAG, "RashedAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observes window and content state changes for UI context if needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "RashedAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceActive.value = false
        Log.d(TAG, "RashedAccessibilityService destroyed")
    }

    /**
     * Searches for a visible node containing the target text and performs click.
     */
    fun clickByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val matchedNodes = rootNode.findAccessibilityNodeInfosByText(text)

        for (node in matchedNodes) {
            if (node.isVisibleToUser) {
                if (performClickOnNodeOrParent(node)) {
                    return true
                }
            }
        }
        return false
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    /**
     * Types text into the currently focused or first available editable field.
     */
    fun typeIntoFocusedField(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        var targetNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (targetNode == null) {
            targetNode = findFirstEditableNode(rootNode)
        }

        if (targetNode != null && targetNode.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        return false
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Scrolls the active window.
     */
    fun performScroll(forward: Boolean): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }

        return performScrollOnScrollableNode(rootNode, action)
    }

    private fun performScrollOnScrollableNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) {
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (performScrollOnScrollableNode(child, action)) {
                return true
            }
        }
        return false
    }

    /**
     * Extracts visible text from the current active window.
     */
    fun extractVisibleText(): List<String> {
        val list = mutableListOf<String>()
        val rootNode = rootInActiveWindow ?: return list
        collectVisibleText(rootNode, list)
        return list
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, list: MutableList<String>) {
        if (node.isVisibleToUser) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && !list.contains(text)) {
                list.add(text)
            }
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrBlank() && !list.contains(desc)) {
                list.add(desc)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectVisibleText(child, list)
        }
    }
}
