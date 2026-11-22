package com.nova.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NovaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: NovaAccessibilityService? = null
            private set
    }

    private val indexedNodes = mutableListOf<AccessibilityNodeInfo>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        indexedNodes.clear()
        instance = null
        super.onDestroy()
    }

    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotificationsShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun performTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 95)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun performLongPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 650)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun performScroll(direction: String): Boolean {
        val metrics = resources.displayMetrics
        val cx = metrics.widthPixels / 2f
        val startY = if (direction == "up") metrics.heightPixels * 0.28f else metrics.heightPixels * 0.78f
        val endY = if (direction == "up") metrics.heightPixels * 0.78f else metrics.heightPixels * 0.22f

        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 320)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun performHorizontalSwipe(direction: String): Boolean {
        val metrics = resources.displayMetrics
        val cy = metrics.heightPixels / 2f
        val startX = if (direction == "right") metrics.widthPixels * 0.20f else metrics.widthPixels * 0.80f
        val endX = if (direction == "right") metrics.widthPixels * 0.80f else metrics.widthPixels * 0.20f

        val path = Path().apply {
            moveTo(startX, cy)
            lineTo(endX, cy)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 280)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return "No active screen content available."
        val collector = StringBuilder()
        collectNodeText(root, collector)
        return collector.toString().trim().take(1500).ifBlank { "Screen has no readable text." }
    }

    @Synchronized
    fun dumpInteractiveScreen(): String {
        indexedNodes.clear()
        val root = rootInActiveWindow ?: return "NO_ACTIVE_WINDOW (Enable NOVA Accessibility Service in Settings)"
        val metrics = resources.displayMetrics
        val pkg = root.packageName?.toString().orEmpty()
        val items = mutableListOf<String>()
        collectInteractiveNodes(root, items)
        val header = "SCREEN_RES: ${metrics.widthPixels}x${metrics.heightPixels} | FOREGROUND_APP: $pkg\n"
        return header + items.joinToString("\n").ifBlank { readScreenText() }
    }

    private fun collectInteractiveNodes(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || out.size >= 75) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName?.substringAfterLast('/')?.trim().orEmpty()

        val isActionable = node.isClickable || node.isEditable || isAncestorClickable(node)
        val label = when {
            text.isNotBlank() && desc.isNotBlank() && !text.equals(desc, ignoreCase = true) -> "$text ($desc)"
            text.isNotBlank() -> text
            desc.isNotBlank() -> desc
            viewId.isNotBlank() && isActionable -> "id:$viewId"
            else -> ""
        }

        if (label.isNotBlank() || node.isEditable) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 4 && rect.height() > 4) {
                indexedNodes.add(node)
                val idx = indexedNodes.size
                val tag = buildString {
                    if (node.isEditable) append("[INPUT]")
                    if (isActionable) append("[BTN]")
                    if (node.isChecked) append("[CHECKED]")
                }
                val coords = " @(${rect.centerX()},${rect.centerY()})"
                out.add("#$idx $tag ${label.ifBlank { "InputBox" }}$coords".trim())
            }
        }
        for (i in 0 until node.childCount) {
            collectInteractiveNodes(node.getChild(i), out)
        }
    }

    private fun isAncestorClickable(node: AccessibilityNodeInfo?): Boolean {
        var parent = node?.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isClickable) return true
            parent = parent.parent
            depth++
        }
        return false
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?, out: StringBuilder) {
        if (node == null || out.length > 1500) return
        val txt = node.text?.toString()?.trim()
        if (!txt.isNullOrBlank()) {
            out.append(txt).append(". ")
        }
        for (i in 0 until node.childCount) {
            collectNodeText(node.getChild(i), out)
        }
    }

    @Synchronized
    fun clickNodeByIndex(oneBasedIndex: Int): Boolean {
        val target = indexedNodes.getOrNull(oneBasedIndex - 1) ?: return false
        return clickNodeDirectlyOrViaBounds(target)
    }

    fun clickNodeByTextOrDesc(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findExactNodeMatching(root, query)
            ?: findPartialNodeMatching(root, query)
            ?: return false
        return clickNodeDirectlyOrViaBounds(target)
    }

    private fun clickNodeDirectlyOrViaBounds(target: AccessibilityNodeInfo): Boolean {
        var clickable: AccessibilityNodeInfo? = target
        while (clickable != null && !clickable.isClickable) {
            clickable = clickable.parent
        }
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val rect = Rect()
        target.getBoundsInScreen(rect)
        if (!rect.isEmpty) {
            return performTap(rect.exactCenterX(), rect.exactCenterY())
        }
        return false
    }

    fun typeTextInFocusedOrAnyInput(text: String, submitEnter: Boolean = true): Boolean {
        val root = rootInActiveWindow ?: return false
        val input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditableNode(root)
            ?: return false

        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        input.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val typed = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (typed && submitEnter) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
            // Also try clicking visible Send / Search button if present
            val sendBtn = findExactNodeMatching(root, "Send")
                ?: findExactNodeMatching(root, "Search")
                ?: findExactNodeMatching(root, "id:send")
            if (sendBtn != null) {
                clickNodeDirectlyOrViaBounds(sendBtn)
            }
        }
        return typed
    }

    fun tryClickWhatsAppSend(): Boolean {
        val root = rootInActiveWindow ?: return false
        val sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (!sendNodes.isNullOrEmpty()) {
            return sendNodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return clickNodeByTextOrDesc("Send")
    }

    fun tryPlayFirstYouTubeResult(): Boolean {
        val root = rootInActiveWindow ?: return false
        val targets = listOf(
            "com.google.android.youtube:id/thumbnail",
            "com.google.android.youtube:id/video_info_view",
            "com.google.android.youtube:id/title"
        )
        for (viewId in targets) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                if (clickNodeDirectlyOrViaBounds(nodes.first())) {
                    return true
                }
            }
        }
        return false
    }

    private fun findExactNodeMatching(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val txt = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName?.substringAfterLast('/')?.trim().orEmpty()
        if (txt.equals(query, ignoreCase = true) ||
            desc.equals(query, ignoreCase = true) ||
            "id:$viewId".equals(query, ignoreCase = true)
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findExactNodeMatching(node.getChild(i), query)
            if (found != null) return found
        }
        return null
    }

    private fun findPartialNodeMatching(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val txt = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (txt.contains(query, ignoreCase = true) || desc.contains(query, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findPartialNodeMatching(node.getChild(i), query)
            if (found != null) return found
        }
        return null
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }
}
