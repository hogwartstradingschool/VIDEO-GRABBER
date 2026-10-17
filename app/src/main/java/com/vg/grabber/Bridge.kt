package com.vg.grabber

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.launch
import org.json.JSONObject

// JS (panel.js UI) <-> Kotlin Engine. UI wahi purani, bas fetch('/api/..') yahan aata hai.
class Bridge(private val ctx: Context, private val wv: WebView, private val host: Host) {
    interface Host { fun close(); fun pendingUrl(): String?; fun perm(name: String) }

    @JavascriptInterface
    fun api(id: Int, path: String, body: String) {
        Engine.attach(ctx)
        Engine.scope.launch {
            var ok = true
            val txt = try { handle(path, JSONObject(body)) } catch (e: Exception) {
                ok = false
                JSONObject().put("error", (e.message ?: e.toString()).lines().lastOrNull { it.isNotBlank() }?.removePrefix("ERROR: ")?.take(300) ?: "error").toString()
            }
            wv.post { runCatching { wv.evaluateJavascript("window.__vgr($id,$ok,${JSONObject.quote(txt)})", null) } }
        }
    }

    private suspend fun handle(path: String, b: JSONObject): String = when (path) {
        "formats" -> Engine.formats(b.getString("url")).toString()
        "download" -> Engine.start(b.getString("url"), b.optInt("height"), b.optLong("size"), b.optString("title")).toString()
        "jobs" -> Engine.jobsJson()
        "cancel" -> ok(Engine.cancel(b.getInt("id")))
        "pause" -> ok(Engine.pause(b.getInt("id")))
        "resume" -> ok(Engine.resume(b.getInt("id")))
        "remove" -> ok(Engine.remove(b.getInt("id")))
        "open-folder", "open" -> { openFolder(); ok(true) }
        "detect" -> {
            var u: String? = host.pendingUrl() ?: Detector.url
            if (u == null || b.optBoolean("clip")) u = clipUrl() ?: u
            JSONObject().put("url", u ?: JSONObject.NULL).toString()
        }
        "close" -> { host.close(); ok(true) }
        "perm" -> JSONObject().apply {
            put("overlay", Settings.canDrawOverlays(ctx)); put("a11y", a11yOn()); put("bubble", OverlayService.bubbleVisible)
            put("notif", Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED)
            put("storage", Build.VERSION.SDK_INT > 28 || ctx.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED)
            put("sdk", Build.VERSION.SDK_INT)
        }.toString()
        "perm-do" -> { host.perm(b.getString("name")); ok(true) }
        else -> throw IllegalArgumentException("unknown api $path")
    }

    private fun ok(v: Boolean) = JSONObject().put("ok", v).toString()

    private fun a11yOn(): Boolean =
        (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(ctx.packageName)

    private fun clipUrl(): String? {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
        return Detector.normalize(t, false)
    }

    private fun openFolder() {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMovies%2FVideoGrabber"), "vnd.android.document/directory")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { ctx.startActivity(i) } catch (e: Exception) {
            runCatching { ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
