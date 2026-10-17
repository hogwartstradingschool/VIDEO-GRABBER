package com.vg.grabber

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OverlayService : Service() {
    companion object {
        @Volatile var running = false
        @Volatile var bubbleVisible = false
        const val ACTION_SHOW = "vg.show"
        const val ACTION_STOP = "vg.stop"
        const val ACTION_KEEP = "vg.keep"
        const val EXTRA_URL = "url"
        private const val CH = "vg"
    }

    private lateinit var wm: WindowManager
    private var bubble: LinearLayout? = null
    private var badge: TextView? = null
    private var panel: FrameLayout? = null
    private var webView: WebView? = null
    private var pending: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(i: Intent?): IBinder? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val host = object : Bridge.Host {
        override fun close() { main.post { closePanel() } }
        override fun pendingUrl(): String? = pending
        override fun perm(name: String) {}
    }

    // ---------- lifecycle ----------
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startForeground(1, notif("Video Grabber active", -1))
        if (!::wm.isInitialized) {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            Engine.attach(this)
            scope.launch {
                while (isActive) {
                    delay(1000)
                    refreshBubble()
                    val s = Engine.summary()
                    getSystemService(NotificationManager::class.java).notify(1, notif(s.first, s.second))
                    maybeStop()
                }
            }
        }
        running = true
        when {
            i?.action == ACTION_STOP -> {
                Prefs.set(this, false); removeBubble()
                if (Engine.activeCount() == 0 && panel == null) { stopSelf(); return START_NOT_STICKY }
            }
            i?.action == ACTION_SHOW -> {
                if (Prefs.on(this)) addBubble()
                showPanel(i!!.getStringExtra(EXTRA_URL))
            }
            i?.action == ACTION_KEEP -> {}
            i == null -> if (Prefs.on(this)) addBubble()
            else -> { Prefs.set(this, true); addBubble() }
        }
        return START_STICKY
    }

    private fun maybeStop() {
        if (!Prefs.on(this) && panel == null && Engine.activeCount() == 0) stopSelf()
    }

    override fun onDestroy() {
        running = false; bubbleVisible = false; Detector.listener = null
        runCatching { bubble?.let { wm.removeView(it) } }
        runCatching { panel?.let { wm.removeView(it) } }
        runCatching { webView?.destroy() }
        scope.cancel()
        super.onDestroy()
    }

    private fun notif(text: String, progress: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null)
            nm.createNotificationChannel(NotificationChannel(CH, "Video Grabber", NotificationManager.IMPORTANCE_LOW))
        val b = Notification.Builder(this, CH).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Video Grabber").setContentText(text).setOngoing(true)
        if (progress in 0..100) b.setProgress(100, progress, false)
        return b.build()
    }

    // ---------- bubble (puppy pill, jaisa extension ka Download pill) ----------
    private fun addBubble() {
        if (bubble != null) return
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(4), dp(12), dp(4))
            background = GradientDrawable().apply { cornerRadius = dp(40).toFloat(); setColor(0xD0281E56.toInt()); setStroke(dp(2), 0x73FFFFFF) }
            elevation = dp(8).toFloat()
        }
        val ic = ImageView(this).apply { setImageResource(R.drawable.logo); scaleType = ImageView.ScaleType.FIT_CENTER }
        pill.addView(ic, LinearLayout.LayoutParams(dp(38), dp(38)))
        pill.addView(TextView(this).apply { text = "Download"; textSize = 14f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        val bd = TextView(this).apply { textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(dp(8), dp(1), dp(8), dp(1)) }
        pill.addView(bd, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        badge = bd

        val lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(220)
        }
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        pill.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - dx).toInt(); val my = (e.rawY - dy).toInt()
                    if (Math.abs(mx) > dp(8) || Math.abs(my) > dp(8)) moved = true
                    if (moved) { lp.x = sx + mx; lp.y = sy + my; wm.updateViewLayout(pill, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) showPanel(null)
            }
            true
        }
        wm.addView(pill, lp)
        bubble = pill; bubbleVisible = true
        Detector.listener = { refreshBubble() }
        refreshBubble()
    }

    private fun removeBubble() {
        runCatching { bubble?.let { wm.removeView(it) } }
        bubble = null; badge = null; bubbleVisible = false; Detector.listener = null
    }

    private fun refreshBubble() {
        val b = bubble ?: return
        val u = Detector.url
        val n = Engine.activeCount()
        b.alpha = if (u != null || n > 0) 1f else 0.72f
        badge?.apply {
            if (n > 0) { text = "↓$n"; visibility = View.VISIBLE; background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0x66FFFFFF) } }
            else if (u != null) {
                text = "●"; visibility = View.VISIBLE; background = null
                setTextColor(if (Detector.isKnown(u)) 0xFF2ECB8F.toInt() else 0xFFFFA24C.toInt())
            } else visibility = View.GONE
        }
    }

    // ---------- panel: wahi purani UI (panel.js) WebView mein ----------
    private fun showPanel(forced: String?) {
        if (panel != null) return
        pending = forced
        val wv = WebView(this)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()
        wv.addJavascriptInterface(Bridge(this, wv, host), "VGA")
        wv.loadUrl("file:///android_asset/ui/overlay.html")
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { closePanel(); return true }
                return super.dispatchKeyEvent(e)
            }
        }
        root.addView(wv, FrameLayout.LayoutParams(-1, -1))
        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT)
        wm.addView(root, lp)
        panel = root; webView = wv
    }

    private fun closePanel() {
        runCatching { panel?.let { wm.removeView(it) } }
        runCatching { webView?.destroy() }
        panel = null; webView = null; pending = null
        maybeStop()
    }
}
