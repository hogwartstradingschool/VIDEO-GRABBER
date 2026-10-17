package com.vg.grabber

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

class ShareActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val txt = intent.getStringExtra(Intent.EXTRA_TEXT)
        val url = Detector.normalize(txt, false)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Pehle Video Grabber app khol kar permissions do", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            val i = Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_SHOW).putExtra(OverlayService.EXTRA_URL, url)
            ContextCompat.startForegroundService(this, i)
        }
        finish()
    }
}
