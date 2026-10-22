package com.example.screenread

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private var tick: Runnable? = null

    private var lastNodeText = ""
    private var lastOcrText = ""
    private var autoOn = false

    private var hitInfo = ""
    private var lastRuleKey = ""
    private var lastRuleAt = 0L
    private var hitCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        panel = FloatingTextWindow(this, object : FloatingTextWindow.Listener {
            override fun onSwipeUp() { swipe(0.75f, 0.28f) }
            override fun onSwipeDown() { swipe(0.28f, 0.78f) }
            override fun onAutoToggle(want: Boolean): Boolean {
                setAuto(want)
                return autoOn
            }
            override fun onClose() {
                setAuto(false)
                Prefs.setShowPanel(this@TextReaderService, false)
            }
        })

        if (Prefs.showPanel(this)) panel?.show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() == packageName) return
        pending?.let { main.removeCallbacks(it) }
        val r = Runnable { safeRefresh() }
        pending = r
        main.postDelayed(r, 250)
    }

    fun refreshNow() = safeRefresh()

    fun refreshPanel() = render()

    fun panelBounds(): Rect? = panel?.bounds()

    fun syncPanelVisibility() {
        if (Prefs.showPanel(this)) panel?.show() else panel?.hide()
    }

    fun onOcrText(text: String) {
        main.post {
            lastOcrText = text
            checkRules(text)
            render()
            if (Prefs.autoCopy(this)) copy(text)
        }
    }

    private fun checkRules(text: String) {
        if (!Prefs.rulesEnabled(this)) return
        val rules = KeywordEngine.parse(Prefs.rules(this))
        if (rules.isEmpty()) return

        val hit = KeywordEngine.match(rules, text) ?: return

        val key = hit.keyword + ":" + hit.action
        val now = SystemClock.elapsedRealtime()
        if (key == lastRuleKey && now - lastRuleAt < 2500) return
        lastRuleKey = key
        lastRuleAt = now

        when (hit.action) {
            KeywordEngine.Action.UP -> swipe(0.75f, 0.28f)
            KeywordEngine.Action.DOWN -> swipe(0.28f, 0.78f)
            KeywordEngine.Action.STOP -> setAuto(false)
            KeywordEngine.Action.COPY -> copy(text)
        }

        hitCount++
        hitInfo = "命中「${hit.keyword}」→${KeywordEngine.label(hit.action)} x$hitCount"
        Log.d("TextReader", "规则命中: $hitInfo")
    }

    private fun safeRefresh() {
        try {
            val text = collectText()
            if (text.isNotBlank()) lastNodeText = text
            render()
        } catch (e: Exception) {
            Log.w("TextReader", "读取控件文字失败", e)
        }
    }

    private fun collectText(): String {
        val roots = ArrayList<AccessibilityNodeInfo>(4)
        windows?.forEach { w -> w.root?.let { roots.add(it) } }
        if (roots.isEmpty()) rootInActiveWindow?.let { roots.add(it) }
        if (roots.isEmpty()) return ""
        return TextExtractor.extract(roots, packageName)
    }

    private fun render() {
        val sb = StringBuilder()
        if (lastOcrText.isNotBlank()) sb.append("【画面文字】\n").append(lastOcrText)
        if (lastNodeText.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("【控件文字】\n").append(lastNodeText)
        }
        panel?.update(sb.toString(), statusText())
    }

    private fun statusText(): String {
        val ocr = if (ScreenCaptureService.running) "画面:开" else "画面:关"
        val rules = if (Prefs.rulesEnabled(this)) "规则:开" else "规则:关"
        val base = "$ocr  $rules"
        return if (hitInfo.isEmpty()) base else "$base  $hitInfo"
    }

    private fun copy(text: String) {
        if (text.isBlank()) return
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("screen_text", text))
    }

    private fun swipe(fromRatio: Float, toRatio: Float, duration: Long = 300L): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val path = Path().apply {
            moveTo(x, dm.heightPixels * fromRatio)
            lineTo(x, dm.heightPixels * toRatio)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return try {
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.w("TextReader", "手势失败", e)
            false
        }
    }

    fun setAuto(on: Boolean) {
        autoOn = on
        panel?.setAuto(on)
        tick?.let { main.removeCallbacks(it) }
        tick = null
        if (!on) return

        val r = object : Runnable {
            override fun run() {
                if (!autoOn) return
                if (!Prefs.rulesEnabled(this@TextReaderService)) swipe(0.75f, 0.28f)
                main.postDelayed(this, Prefs.intervalSec(this@TextReaderService) * 1000L)
            }
        }
        tick = r
        main.postDelayed(r, 800L)
    }

    fun resetHitCount() {
        hitCount = 0
        hitInfo = ""
        render()
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
        setAuto(false)
        pending?.let { main.removeCallbacks(it) }
        pending = null
        panel?.hide()
        panel = null
        if (instance === this) instance = null
    }
}
