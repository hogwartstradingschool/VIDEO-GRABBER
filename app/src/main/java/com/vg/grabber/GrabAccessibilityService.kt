package com.vg.grabber

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GrabAccessibilityService : AccessibilityService() {
    private var last = 0L
    private val ids = listOf("url_bar", "mozac_browser_toolbar_url_view", "location_bar_edit_text",
        "url_field", "search_box_text", "addressbar", "url")

    override fun onServiceConnected() { super.onServiceConnected(); Engine.attach(applicationContext) }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        val now = SystemClock.uptimeMillis()
        if (now - last < 500) return
        last = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        Detector.update(pkg, findUrl(root, pkg))
    }

    private fun findUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        for (id in ids) {
            val n = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            for (node in n) {
                val u = Detector.normalize(node.text?.toString(), true)
                if (u != null) return u
            }
        }
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var count = 0
        while (stack.isNotEmpty() && count < 600) {
            val n = stack.removeLast(); count++
            val idName = n.viewIdResourceName ?: ""
            val bareOk = idName.contains("url", true) || idName.contains("address", true)
            Detector.normalize(n.text?.toString(), bareOk)?.let { return it }
            Detector.normalize(n.contentDescription?.toString(), false)?.let { return it }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    override fun onInterrupt() {}
}
