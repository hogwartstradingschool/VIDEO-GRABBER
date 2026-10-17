package com.vg.grabber

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var box: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sv = ScrollView(this)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 60) }
        sv.addView(box)
        setContentView(sv)
    }

    override fun onResume() { super.onResume(); render() }

    private fun a11yOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(packageName)
    }

    private fun row(title: String, ok: Boolean, action: () -> Unit) {
        val btn = Button(this).apply {
            text = (if (ok) "✅ " else "❌ ") + title
            isAllCaps = false
            setOnClickListener { action() }
        }
        box.addView(btn)
    }

    private fun render() {
        box.removeAllViews()
        box.addView(TextView(this).apply { text = "⬇ Video Grabber"; textSize = 26f; setTextColor(Color.BLACK) })
        box.addView(TextView(this).apply {
            text = "\nSab ✅ karo, phir START dabao. Floating shutter bubble har app ke upar aa jayega."
            textSize = 15f
        })
        row("1. Overlay (Display over other apps)", Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        row("2. Accessibility (link detect karne ke liye)", a11yOn()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Agar greyed-out ho: App info > ⋮ > Allow restricted settings", Toast.LENGTH_LONG).show()
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            row("3. Notifications", ok) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
        }
        if (Build.VERSION.SDK_INT <= 28) {
            val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            row("4. Storage", ok) { requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 2) }
        }
        box.addView(Button(this).apply {
            text = if (OverlayService.running) "⏹ STOP shutter" else "▶ START shutter"
            textSize = 18f
            setOnClickListener {
                if (OverlayService.running) {
                    startService(Intent(this@MainActivity, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
                    postDelayed({ render() }, 400)
                } else if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Pehle Overlay permission do", Toast.LENGTH_SHORT).show()
                } else {
                    ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, OverlayService::class.java))
                    postDelayed({ render() }, 600)
                }
            }
        })
        box.addView(TextView(this).apply {
            textSize = 14f
            text = "\nKaise use karein:\n• Browser mein video page kholo → bubble GREEN ho jata hai → bubble dabao → quality chuno.\n" +
                "• YouTube/Facebook/Instagram app mein: Share → Copy link, phir bubble dabao (ya Share → Video Grabber).\n" +
                "• Files: Gallery/Files → Movies/VideoGrabber"
        })
    }
}
