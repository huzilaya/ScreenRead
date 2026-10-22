package com.example.screenread

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.math.min

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.example.screenread.STOP_CAPTURE"

        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIF_ID = 2001
        private const val MIN_INTERVAL_MS = 650L

        @Volatile
        var running = false
            private set
    }

    private val main = Handler(mainLooper)
    private val workerThread = HandlerThread("ocr-worker").apply { start() }
    private val worker = Handler(workerThread.looper)

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null

    private var busy = false
    private var lastAt = 0L

    private val blackPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        startCapture(code, data)
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "画面识别", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("正在识别画面文字")
            .setContentText("点按可回到应用")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= 29)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    private fun startCapture(code: Int, data: Intent) {
        if (projection != null) return

        val mgr = getSystemService(MediaProjectionManager::class.java)
        val p = mgr.getMediaProjection(code, data) ?: run { stopSelf(); return }
        projection = p

        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, main)

        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels

        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        r.setOnImageAvailableListener({ ir -> handleFrame(ir) }, worker)

        virtualDisplay = p.createVirtualDisplay(
            "ocr-capture", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, null
        )

        running = true
        TextReaderService.instance?.refreshPanel()
    }

    private fun handleFrame(ir: ImageReader) {
        val image = try {
            ir.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return

        val now = SystemClock.elapsedRealtime()
        if (busy || now - lastAt < MIN_INTERVAL_MS) {
            image.close()
            return
        }
        busy = true
        lastAt = now

        val bmp = try { prepare(image) } catch (e: Exception) { null }
        image.close()
        if (bmp == null) {
            busy = false
            return
        }

        val input = InputImage.fromBitmap(bmp, rotationDegrees())
        recognizer.process(input)
            .addOnSuccessListener { publish(it) }
            .addOnFailureListener { Log.w("ScreenOcr", "识别失败", it) }
            .addOnCompleteListener {
                bmp.recycle()
                busy = false
            }
    }

    private fun prepare(image: Image): Bitmap? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride

        val raw = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buffer)

        val scale = min(1f, 1080f / image.width)
        val w = (image.width * scale).toInt()
        val h = (image.height * scale).toInt()

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(raw, Rect(0, 0, image.width, image.height), Rect(0, 0, w, h), null)
        raw.recycle()

        val pr = TextReaderService.instance?.panelBounds()
        if (pr != null && pr.width() > 0) {
            canvas.drawRect(
                pr.left * scale, pr.top * scale,
                pr.right * scale, pr.bottom * scale,
                blackPaint
            )
        }
        return out
    }

    private fun publish(text: Text) {
        val lines = text.textBlocks
            .flatMap { it.lines }
            .map { it.text.trim() }
            .filter { it.length > 1 && !isOwnUiText(it) }

        if (lines.isEmpty()) return
        TextReaderService.instance?.onOcrText(lines.joinToString("\n"))
    }

    private fun isOwnUiText(s: String): Boolean {
        val own = listOf(
            "屏幕取字", "复制", "清空", "上滑", "下滑",
            "自动:开", "自动:关", "画面:开", "画面:关"
        )
        return own.any { s == it || s.startsWith("间隔:") || s.startsWith("命中") }
    }

    private fun rotationDegrees(): Int {
        val dm = getSystemService(DisplayManager::class.java)
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return 0
        return when (d.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun releaseAll() {
        running = false
        virtualDisplay?.release(); virtualDisplay = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
    }

    override fun onDestroy() {
        releaseAll()
        runCatching { recognizer.close() }
        workerThread.quitSafely()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        TextReaderService.instance?.refreshPanel()
        super.onDestroy()
    }
}
