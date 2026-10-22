package com.example.screenread

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TextReaderService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TextReaderService? = null
            private set
    }

    private val main = Handler(Looper.getMainLooper())
    private var panel: FloatingTextWindow? = null
    private var pending: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        panel = FloatingTextWindow(this) { Prefs.setShowPanel(this, false) }
        if (Prefs.showPanel(this)) panel?.show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() == packageName) return
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        pending?.let { main.removeCallbacks(it) }
        val r = Runnable { safeRefresh() }
        pending = r
        main.postDelayed(r, 200)
    }

    fun refreshNow() = safeRefresh()

    fun syncPanelVisibility() {
        if (Prefs.showPanel(this)) panel?.show() else panel?.hide()
    }

    private fun safeRefresh() {
        try {
            val text = collectText()
            if (text.isBlank()) return
            panel?.update(text)
            if (Prefs.autoCopy(this)) {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("screen_text", text))
            }
        } catch (e: Exception) {
            Log.w("TextReader", "读取屏幕文字失败", e)
        }
    }

    private fun collectText(): String {
        val roots = ArrayList<AccessibilityNodeInfo>(4)
        windows?.forEach { w -> w.root?.let { roots.add(it) } }
        if (roots.isEmpty()) rootInActiveWindow?.let { roots.add(it) }
        if (roots.isEmpty()) return ""
        return TextExtractor.extract(roots, packageName)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
        panel?.hide()
        panel = null
        if (instance === this) instance = null
    }
}
