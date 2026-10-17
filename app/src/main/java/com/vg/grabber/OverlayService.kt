package com.vg.grabber

import android.app.*
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.*
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import java.io.File

class OverlayService : Service() {
    companion object {
        @Volatile var running = false
        const val ACTION_SHOW = "vg.show"
        const val ACTION_STOP = "vg.stop"
        const val EXTRA_URL = "url"
        private const val CH = "vg"
    }

    private class Opt(val label: String, val sel: String, val audio: Boolean)

    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: FrameLayout? = null
    private var titleV: TextView? = null
    private var statusV: TextView? = null
    private var listBox: LinearLayout? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var inited = false

    override fun onBind(i: Intent?): IBinder? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- lifecycle ----------
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        startForeground(1, notif("Shutter ON - bubble dabao", -1))
        if (!running) {
            running = true
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            addBubble()
            Detector.listener = { refreshBubble() }
            scope.launch(Dispatchers.IO) { runCatching { ensureInit(); updateYtDlp() } }
        }
        if (i?.action == ACTION_SHOW) showPanel(i.getStringExtra(EXTRA_URL))
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        Detector.listener = null
        runCatching { bubble?.let { wm.removeView(it) } }
        runCatching { panel?.let { wm.removeView(it) } }
        scope.cancel()
        super.onDestroy()
    }

    @Synchronized private fun ensureInit() {
        if (inited) return
        YoutubeDL.getInstance().init(applicationContext)
        FFmpeg.getInstance().init(applicationContext)
        inited = true
    }

    // yt-dlp ko latest karna (sites roz badalti hain). Reflection => library version se compile nahi tootta.
    private fun updateYtDlp() {
        runCatching {
            val y = YoutubeDL.getInstance()
            val m = y.javaClass.methods.first { it.name == "updateYoutubeDL" && it.parameterTypes.size == 2 }
            val ch = m.parameterTypes[1]
            var inst: Any? = null
            ch.declaredFields.firstOrNull { it.name.contains("STABLE") }?.let { it.isAccessible = true; inst = it.get(null) }
            if (inst == null) {
                ch.declaredClasses.firstOrNull { it.simpleName.contains("STABLE") }?.let { c ->
                    inst = c.getDeclaredField("INSTANCE").get(null)
                }
            }
            m.invoke(y, applicationContext, inst)
        }
    }

    // ---------- notification ----------
    private fun notif(text: String, progress: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null)
            nm.createNotificationChannel(NotificationChannel(CH, "Video Grabber", NotificationManager.IMPORTANCE_LOW))
        val b = Notification.Builder(this, CH).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Video Grabber").setContentText(text).setOngoing(true)
        if (progress in 0..100) b.setProgress(100, progress, false)
        return b.build()
    }
    private fun notifyNow(text: String, p: Int) =
        getSystemService(NotificationManager::class.java).notify(1, notif(text, p))

    // ---------- bubble ----------
    private fun addBubble() {
        val tv = TextView(this).apply {
            text = "⬇"; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC555555.toInt()) }
        }
        val lp = WindowManager.LayoutParams(dp(54), dp(54), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(220)
        }
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - dx).toInt(); val my = (e.rawY - dy).toInt()
                    if (Math.abs(mx) > dp(8) || Math.abs(my) > dp(8)) moved = true
                    if (moved) { lp.x = sx + mx; lp.y = sy + my; wm.updateViewLayout(tv, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) showPanel(null)
            }
            true
        }
        wm.addView(tv, lp)
        bubble = tv
        refreshBubble()
    }

    private fun refreshBubble() {
        val u = Detector.url
        val c = when {
            u == null -> 0xCC555555.toInt()
            Detector.isKnown(u) -> 0xFF1DB954.toInt()
            else -> 0xFFFF9800.toInt()
        }
        (bubble?.background as? GradientDrawable)?.setColor(c)
    }

    // ---------- panel ----------
    private fun tv(t: String, size: Float, color: Int) = TextView(this).apply { text = t; textSize = size; setTextColor(color) }

    private fun showPanel(forced: String?) {
        if (panel != null) return
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { closePanel(); return true }
                return super.dispatchKeyEvent(e)
            }
        }
        root.setBackgroundColor(0x88000000.toInt())
        root.setOnClickListener { closePanel() }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply { setColor(0xFF202124.toInt()); cornerRadius = dp(18).toFloat() }
            setOnClickListener { }
        }
        val title = tv("⬇ Video Grabber", 18f, Color.WHITE)
        val status = tv("", 14f, 0xFFBBBBBB.toInt())
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sc = ScrollView(this).apply { addView(list) }
        card.addView(title); card.addView(status)
        card.addView(sc, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.42).toInt()))
        val close = Button(this).apply { text = "Close"; isAllCaps = false; setOnClickListener { closePanel() } }
        card.addView(close)
        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(10), 0, dp(10), dp(24)) })
        titleV = title; statusV = status; listBox = list

        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        wm.addView(root, lp)
        panel = root
        status.text = "Link check ho raha hai…"
        root.postDelayed({ start(forced) }, 250)
    }

    private fun clipUrl(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        return Detector.normalize(t, false)
    }

    private fun start(forced: String?) {
        listBox?.removeAllViews()
        val url = forced ?: Detector.url ?: clipUrl()
        if (url == null) {
            statusV?.text = "❌ Link nahi mila.\nBrowser mein video page kholo, ya app mein Share → Copy link karo, phir dobara try karo."
            addRow("📋 Clipboard se link lo") { start(null) }
            return
        }
        analyze(url)
    }

    private fun addRow(text: String, click: () -> Unit) {
        listBox?.addView(Button(this).apply {
            this.text = text; isAllCaps = false; textSize = 16f; setOnClickListener { click() }
        })
    }

    private fun sz(a: Long, b: Long) = if (a > 0) a else b
    private fun mb(x: Long) = if (x > 0) " • ~" + (if (x > 1_000_000_000) "%.1f GB".format(x / 1e9) else "${x / 1_000_000} MB") else ""

    private fun analyze(url: String) {
        statusV?.text = "🔎 Qualities dhoondh raha hu…\n$url"
        listBox?.removeAllViews()
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    ensureInit()
                    val rq = YoutubeDLRequest(url); rq.addOption("--no-playlist")
                    YoutubeDL.getInstance().getInfo(rq)
                }
            }
            val info = r.getOrNull()
            if (info == null) {
                statusV?.text = "❌ Is link se video nahi mili:\n" + (r.exceptionOrNull()?.message ?: "").lines().last().take(180)
                addRow("📋 Clipboard se link lo") { start(null) }
                return@launch
            }
            titleV?.text = info.title ?: "Video"
            statusV?.text = "Quality chuno:"
            val fm = info.formats ?: emptyList()
            val audioSize = fm.filter { (it.vcodec ?: "none") == "none" && (it.acodec ?: "none") != "none" }
                .maxOfOrNull { sz(it.fileSize, it.fileSizeApproximate) } ?: 0L
            val heights = fm.filter { (it.vcodec ?: "none") != "none" && it.height > 0 }.map { it.height }.distinct().sortedDescending()
            val opts = ArrayList<Opt>()
            opts.add(Opt("🏆 Best available", "bv*+ba/b", false))
            for (h in heights) {
                val vids = fm.filter { it.height == h && (it.vcodec ?: "none") != "none" }
                val vs = vids.maxOfOrNull { sz(it.fileSize, it.fileSizeApproximate) } ?: 0L
                val needA = vids.none { (it.acodec ?: "none") != "none" }
                val total = if (vs > 0) vs + (if (needA) audioSize else 0L) else 0L
                opts.add(Opt("🎬 ${h}p${mb(total)}",
                    "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b", false))
            }
            opts.add(Opt("🎵 Sirf Audio (MP3)${mb(audioSize)}", "ba/b", true))
            listBox?.removeAllViews()
            for (o in opts) addRow(o.label) { download(url, o) }
        }
    }

    private fun download(url: String, o: Opt) {
        listBox?.removeAllViews()
        statusV?.text = "⏳ Download shuru…"
        val dir = File(cacheDir, "dl/${System.currentTimeMillis()}").apply { mkdirs() }
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    ensureInit()
                    val rq = YoutubeDLRequest(url)
                    rq.addOption("--no-playlist")
                    rq.addOption("-f", o.sel)
                    if (o.audio) { rq.addOption("-x"); rq.addOption("--audio-format", "mp3") }
                    else rq.addOption("--merge-output-format", "mp4")
                    rq.addOption("-o", dir.absolutePath + "/%(title).80s.%(ext)s")
                    YoutubeDL.getInstance().execute(rq, "vg-" + System.currentTimeMillis()) { p, _, _ ->
                        main.post {
                            statusV?.text = "⏳ Downloading… ${p.toInt()}%"
                            notifyNow("Downloading ${p.toInt()}%", p.toInt())
                        }
                    }
                    val f = dir.listFiles()?.filter { !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && it.length() > 0 }
                        ?.maxByOrNull { it.length() } ?: error("File nahi bani")
                    saveFile(f)
                }
            }
            val ok = r.getOrNull()
            if (ok != null) {
                statusV?.text = "✅ Save ho gayi!\nGallery / Files → $ok"
                notifyNow("✅ Download complete", -1)
            } else {
                statusV?.text = "❌ Download fail:\n" + (r.exceptionOrNull()?.message ?: "").lines().last().take(180)
                notifyNow("Download fail", -1)
            }
        }
    }

    private fun saveFile(f: File): String {
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"
        val audio = mime.startsWith("audio")
        val rel = if (audio) "Music/VideoGrabber" else "Movies/VideoGrabber"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }
            val coll = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = contentResolver.insert(coll, cv) ?: error("MediaStore insert fail")
            contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
        } else {
            val d = File(Environment.getExternalStoragePublicDirectory(
                if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES), "VideoGrabber")
            d.mkdirs()
            val t = File(d, f.name); f.copyTo(t, true)
            MediaScannerConnection.scanFile(this, arrayOf(t.absolutePath), null, null)
        }
        f.parentFile?.deleteRecursively()
        return rel
    }

    private fun closePanel() {
        runCatching { panel?.let { wm.removeView(it) } }
        panel = null
    }
}
