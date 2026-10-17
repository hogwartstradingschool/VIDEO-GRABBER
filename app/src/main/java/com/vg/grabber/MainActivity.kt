package com.vg.grabber

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

class MainActivity : Activity(), Bridge.Host {
    private lateinit var wv: WebView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = 0xFF0A0A16.toInt(); window.navigationBarColor = 0xFF0A0A16.toInt()
        Engine.attach(this)
        publishShareTarget()
        wv = WebView(this)
        wv.setBackgroundColor(0xFF0A0A16.toInt())
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()
        wv.addJavascriptInterface(Bridge(this, wv, this), "VGA")
        wv.loadUrl("file:///android_asset/ui/index.html")
        val root = FrameLayout(this)
        root.fitsSystemWindows = true
        root.addView(wv, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)
    }

    // Share sheet ki upar wali "direct share" row mein Video Grabber aaye (sab se pehle nazar aaye)
    private fun publishShareTarget() {
        runCatching {
            val sc = ShortcutInfoCompat.Builder(this, "vg_share")
                .setShortLabel("Video Grabber").setLongLabel("Video Grabber")
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(Intent(this, ShareActivity::class.java).setAction(Intent.ACTION_SEND))
                .setCategories(setOf("com.vg.grabber.category.TEXT_SHARE_TARGET"))
                .setLongLived(true).build()
            ShortcutManagerCompat.pushDynamicShortcut(this, sc)
        }
    }

    override fun onResume() { super.onResume(); wv.evaluateJavascript("window.__vgperm&&window.__vgperm()", null) }
    override fun onBackPressed() { finish() }

    override fun close() {}
    override fun pendingUrl(): String? = null

    override fun perm(name: String) {
        runOnUiThread {
            when (name) {
                "overlay" -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                "a11y" -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    Toast.makeText(this, "Greyed-out ho to: App info > ⋮ > Allow restricted settings", Toast.LENGTH_LONG).show()
                }
                "notif" -> if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                "storage" -> if (Build.VERSION.SDK_INT <= 28) requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 2)
                "bubble" -> {
                    if (OverlayService.bubbleVisible) {
                        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
                    } else if (!Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "Pehle Overlay permission do", Toast.LENGTH_SHORT).show()
                    } else {
                        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
                    }
                }
            }
        }
    }
}
