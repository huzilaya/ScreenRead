package com.example.screenread

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusA11y: TextView
    private lateinit var statusOcr: TextView
    private lateinit var statusOverlay: TextView
    private lateinit var etRules: EditText

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val data = res.data
        if (res.resultCode == RESULT_OK && data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_CODE, res.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(this, i)
            statusOcr.postDelayed({ renderStatus() }, 600)
        } else {
            toast("未授权屏幕捕获")
        }
    }

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusA11y = findViewById(R.id.statusA11y)
        statusOcr = findViewById(R.id.statusOcr)
        statusOverlay = findViewById(R.id.statusOverlay)
        etRules = findViewById(R.id.etRules)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOcr).setOnClickListener {
            if (ScreenCaptureService.running) {
                startService(
                    Intent(this, ScreenCaptureService::class.java)
                        .setAction(ScreenCaptureService.ACTION_STOP)
                )
                statusOcr.postDelayed({ renderStatus() }, 300)
            } else {
                val mgr = getSystemService(MediaProjectionManager::class.java)
                captureLauncher.launch(mgr.createScreenCaptureIntent())
            }
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        etRules.setText(Prefs.rules(this))
        findViewById<Button>(R.id.btnSaveKw).setOnClickListener {
            Prefs.setRules(this, etRules.text.toString())
            val n = KeywordEngine.parse(etRules.text.toString()).size
            toast("已保存 $n 条规则")
        }

        val swRules = findViewById<Switch>(R.id.swRules)
        swRules.isChecked = Prefs.rulesEnabled(this)
        swRules.setOnCheckedChangeListener { _, v -> Prefs.setRulesEnabled(this, v) }

        val swPanel = findViewById<Switch>(R.id.swPanel)
        swPanel.isChecked = Prefs.showPanel(this)
        swPanel.setOnCheckedChangeListener { _, v ->
            Prefs.setShowPanel(this, v)
            TextReaderService.instance?.syncPanelVisibility()
        }

        val swCopy = findViewById<Switch>(R.id.swAutoCopy)
        swCopy.isChecked = Prefs.autoCopy(this)
        swCopy.setOnCheckedChangeListener { _, v -> Prefs.setAutoCopy(this, v) }
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    private fun renderStatus() {
        val a11y = isA11yEnabled()
        statusA11y.text = if (a11y) "已开启" else "未开启，点右侧按钮"
        statusA11y.setTextColor(if (a11y) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        val ocr = ScreenCaptureService.running
        statusOcr.text = if (ocr) "识别中…（点按钮停止）" else "未启动（需授权录屏）"
        statusOcr.setTextColor(if (ocr) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        val overlay = Settings.canDrawOverlays(this)
        statusOverlay.text = if (overlay) "已授权" else "未授权（将使用无障碍覆盖层）"
        statusOverlay.setTextColor(if (overlay) 0xFF2E7D32.toInt() else 0xFFF9A825.toInt())

        findViewById<Button>(R.id.btnOcr).text = if (ocr) "停止" else "启动"
    }

    private fun isA11yEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(0)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
