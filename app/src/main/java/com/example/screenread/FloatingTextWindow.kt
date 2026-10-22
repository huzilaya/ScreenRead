package com.example.screenread

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
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
    private val onClose: (() -> Unit)? = null
) {

    private val wm = ctx.getSystemService(WindowManager::class.java)

    private var root: View? = null
    private var txt: TextView? = null
    private var counter: TextView? = null
    private var scroll: ScrollView? = null

    private var dragDx = 0f
    private var dragDy = 0f

    fun show() {
        if (root != null) return

        val v = LayoutInflater.from(ctx).inflate(R.layout.view_float_panel, null, false)
        txt = v.findViewById(R.id.txtFloat)
        counter = v.findViewById(R.id.txtCount)
        scroll = v.findViewById(R.id.scrollFloat)

        val screenW = if (Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.bounds.width()
        } else {
            ctx.resources.displayMetrics.widthPixels
        }

        val lp = WindowManager.LayoutParams(
            screenW - dp(24),
            WindowManager.LayoutParams.WRAP_CONTENT,
            resolveType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(120)
        }

        v.findViewById<View>(R.id.btnCopy).setOnClickListener {
            val text = txt?.text?.toString().orEmpty()
            if (text.isBlank()) return@setOnClickListener
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("screen_text", text))
            toast("已复制 ${text.length} 字")
        }

        v.findViewById<View>(R.id.btnClear).setOnClickListener {
            txt?.text = ""
            counter?.text = "0 字"
        }

        val collapse = v.findViewById<TextView>(R.id.btnCollapse)
        collapse.setOnClickListener {
            val visible = scroll?.visibility == View.VISIBLE
            scroll?.visibility = if (visible) View.GONE else View.VISIBLE
            collapse.text = if (visible) "＋" else "－"
        }

        v.findViewById<View>(R.id.btnClose).setOnClickListener {
            hide()
            onClose?.invoke()
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

        if (addView(v, lp)) {
            root = v
        } else {
            lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            if (addView(v, lp)) {
                root = v
            } else {
                toast("悬浮窗创建失败，请授予悬浮窗权限")
            }
        }
    }

    private fun addView(v: View, lp: WindowManager.LayoutParams): Boolean =
        try {
            wm.addView(v, lp)
            true
        } catch (e: Exception) {
            false
        }

    private fun resolveType(): Int =
        if (Settings.canDrawOverlays(ctx)) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }

    fun update(text: String) {
        val tv = txt ?: return
        tv.text = text
        counter?.text = "${text.length} 字"
        scroll?.scrollTo(0, 0)
    }

    fun hide() {
        root?.let { r -> runCatching { wm.removeView(r) } }
        root = null
    }

    private fun toast(msg: String) =
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
