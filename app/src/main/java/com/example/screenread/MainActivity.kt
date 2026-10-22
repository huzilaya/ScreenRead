package com.example.screenread

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusA11y: TextView
    private lateinit var statusOverlay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusA11y = findViewById(R.id.statusA11y)
        statusOverlay = findViewById(R.id.statusOverlay)

        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        val swPanel = findViewById<Switch>(R.id.swPanel)
        swPanel.isChecked = Prefs.showPanel(this)
        swPanel.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowPanel(this, checked)
            TextReaderService.instance?.syncPanelVisibility()
        }

        val swCopy = findViewById<Switch>(R.id.swAutoCopy)
        swCopy.isChecked = Prefs.autoCopy(this)
        swCopy.setOnCheckedChangeListener { _, checked -> Prefs.setAutoCopy(this, checked) }
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    private fun renderStatus() {
        val enabled = isA11yEnabled()
        statusA11y.text = if (enabled) "已开启 · 悬浮面板工作中" else "未开启，点击右侧按钮"
        statusA11y.setTextColor(if (enabled) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        val overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        statusOverlay.text = if (overlay) "已授权" else "未授权（将使用无障碍覆盖层）"
        statusOverlay.setTextColor(if (overlay) 0xFF2E7D32.toInt() else 0xFFF9A825.toInt())
    }

    private fun isA11yEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(0)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
