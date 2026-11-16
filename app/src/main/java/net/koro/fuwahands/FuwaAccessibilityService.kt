package net.koro.fuwahands

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reads the on-screen UI tree and performs gestures / global actions.
 * Everything here only works while the user has this service enabled
 * in Android's accessibility settings.
 */
class FuwaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: FuwaAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* read on demand only */ }
    override fun onInterrupt() { }

    // ---------- UI tree ----------

    fun treeJson(): JSONObject {
        val root = rootInActiveWindow ?: throw IllegalStateException("no active window root")
        val counter = intArrayOf(0)
        return nodeJson(root, 0, counter)
    }

    private fun nodeJson(node: AccessibilityNodeInfo, depth: Int, counter: IntArray): JSONObject {
        counter[0]++
        val o = JSONObject()
        o.put("class", node.className?.toString() ?: "")
        node.text?.let { o.put("text", it.toString()) }
        node.contentDescription?.let { o.put("desc", it.toString()) }
        node.viewIdResourceName?.let { o.put("id", it) }
        val r = Rect()
        node.getBoundsInScreen(r)
        o.put("bounds", JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom))
        if (node.isClickable) o.put("clickable", true)
        if (node.isLongClickable) o.put("longClickable", true)
        if (node.isScrollable) o.put("scrollable", true)
        if (node.isEditable) o.put("editable", true)
        if (node.isFocused) o.put("focused", true)
        if (node.isChecked) o.put("checked", true)
        if (!node.isVisibleToUser) o.put("invisible", true)
        if (depth < 40 && counter[0] < 3000 && node.childCount > 0) {
            val kids = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                kids.put(nodeJson(child, depth + 1, counter))
                if (counter[0] >= 3000) break
            }
            o.put("children", kids)
        }
        return o
    }

    // ---------- gestures ----------

    private fun gestureSync(build: () -> GestureDescription): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        val cb = object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(g: GestureDescription?) { ok = false; latch.countDown() }
        }
        if (!dispatchGesture(build(), cb, null)) return false
        latch.await(3, TimeUnit.SECONDS)
        return ok
    }

    fun tap(x: Int, y: Int): Boolean = gestureSync {
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60))
            .build()
    }

    fun longPress(x: Int, y: Int, durationMs: Long): Boolean = gestureSync {
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, durationMs))
            .build()
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean = gestureSync {
        val p = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, durationMs.coerceIn(50, 3000)))
            .build()
    }

    // ---------- global actions & text ----------

    fun global(name: String): Boolean {
        val action = when (name.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power_dialog" -> GLOBAL_ACTION_POWER_DIALOG
            "lock" -> GLOBAL_ACTION_LOCK_SCREEN
            else -> return false
        }
        return performGlobalAction(action)
    }

    fun setText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findFocusedEditable(root) ?: findFirstEditable(root) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findFocusedEditable(c)?.let { return it }
        }
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findFirstEditable(c)?.let { return it }
        }
        return null
    }
}
