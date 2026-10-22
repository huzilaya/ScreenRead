package com.example.screenread

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class FloatingTextWindow(
    private val ctx: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onSwipeUp()
        fun onSwipeDown()
        fun onAutoToggle(want: Boolean): Boolean
        fun onClose()
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)

    private var root: View? = null
    private var txt: TextView? = null
    private var status: TextView? = null
    private var scroll: ScrollView? = null
    private var autoBtn: TextView? = null

    private var autoOn = false
    private var dragDx = 0f
    private var dragDy = 0f

    fun isShowing() = root != null

    fun show() {
        if (root != null) return

        val v = LayoutInflater.from(ctx).inflate(R.layout.view_float_panel, null, false)
        txt = v.findViewById(R.id.txtFloat)
        status = v.findViewById(R.id.txtStatus)
        scroll = v.findViewById(R.id.scrollFloat)
        autoBtn = v.findViewById(R.id.btnAuto)

        val screenW = if (Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.bounds.width()
        } else {
            ctx.resources.displayMetrics.widthPixels
        }

        val lp = WindowManager.LayoutParams(
            screenW - dp(20),
            WindowManager.LayoutParams.WRAP_CONTENT,
            resolveType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(10)
            y = dp(80)
        }

        v.findViewById<TextView>(R.id.btnCopy).setOnClickListener {
            val text = txt?.text?.toString().orEmpty()
            if (text.isBlank()) return@setOnClickListener
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("screen_text", text))
            toast("已复制 ${text.length} 字")
        }

        v.findViewById<TextView>(R.id.btnClear).setOnClickListener {
            txt?.text = ""
        }

        v.findViewById<TextView>(R.id.btnUp).setOnClickListener { listener.onSwipeUp() }
        v.findViewById<TextView>(R.id.btnDown).setOnClickListener { listener.onSwipeDown() }

        autoBtn?.setOnClickListener {
            autoOn = listener.onAutoToggle(!autoOn)
            renderAuto()
        }

        val collapse = v.findViewById<TextView>(R.id.btnCollapse)
        collapse.setOnClickListener {
            val visible = scroll?.visibility == View.VISIBLE
            scroll?.visibility = if (visible) View.GONE else View.VISIBLE
            collapse.text = if (visible) "＋" else "－"
        }

        v.findViewById<TextView>(R.id.btnClose).setOnClickListener {
            listener.onClose()
            hide()
        }

        v.findViewById<View>(R.id.header).setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragDx = e.rawX - lp.x
                    dragDy = e.rawY - lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (e.rawX - dragDx).toInt()
                    lp.y = (e.rawY - dragDy).toInt()
                    root?.let { r -> runCatching { wm.updateViewLayout(r, lp) } }
                    true
                }
                else -> false
            }
        }

        if (!addView(v, lp)) {
            lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            if (!addView(v, lp)) {
                toast("悬浮窗创建失败，请授予悬浮窗权限")
                return
            }
        }
        root = v
    }

    private fun addView(v: View, lp: WindowManager.LayoutParams): Boolean =
        try { wm.addView(v, lp); true } catch (e: Exception) { false }

    private fun resolveType(): Int =
        if (Settings.canDrawOverlays(ctx)) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    fun update(text: String, statusText: String) {
        txt?.text = text
        status?.text = statusText
    }

    fun setAuto(on: Boolean) {
        autoOn = on
        renderAuto()
    }

    private fun renderAuto() {
        autoBtn?.text = if (autoOn) "自动:开" else "自动:关"
        autoBtn?.setTextColor(if (autoOn) 0xFF7CFFB2.toInt() else 0xFF9AA4B2.toInt())
    }

    fun bounds(): Rect? {
        val r = root ?: return null
        if (r.width <= 0 || r.height <= 0) return null
        val loc = IntArray(2)
        r.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + r.width, loc[1] + r.height)
    }

    fun hide() {
        root?.let { r -> runCatching { wm.removeView(r) } }
        root = null
    }

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
