package com.vg.grabber

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// Desktop server.js ka Android port: TURBO engine (32 parallel connections) + yt-dlp fallback,
// pause / resume / cancel, speed graph, same /api contract jo UI (panel.js) use karta hai.
object Engine {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pool = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private lateinit var app: Context
    private var attached = false
    private var inited = false
    private var seq = 0
    private val jobs = ConcurrentHashMap<Int, Dl>()
    private val fmtCache = ConcurrentHashMap<String, Pair<Long, JSONObject>>()
    private const val CHUNK = 2 * 1048576
    private const val CONC = 32
    private val TIERS = listOf(2160 to "4K", 1440 to "2K", 1080 to "FHD", 720 to "HD", 480 to "SD", 360 to "SD")

    class ConvEx(m: String) : IOException(m)
    class TFile { val done: MutableSet<Int> = java.util.Collections.synchronizedSet(HashSet()); @Volatile var sized = false }
    class Ctl { @Volatile var stop = false; val bytes = AtomicLong(); val conns: MutableSet<HttpURLConnection> = ConcurrentHashMap.newKeySet() }
    class Dl(val id: Int, val url: String, val height: Int, val size: Long, var title: String, val conv: Boolean = false) {
        @Volatile var status = "running"; @Volatile var pct = 0.0; @Volatile var speed = 0.0; @Volatile var eta = 0.0
        @Volatile var file = ""; @Volatile var err = ""; @Volatile var merging = false
        @Volatile var dl = 0L; @Volatile var total = 0L
        @Volatile var elapsedBase = 0.0; @Volatile var runStart = 0L
        @Volatile var noTurbo = false
        @Volatile var td: Array<TFile>? = null; @Volatile var tdKey = ""
        @Volatile var ctl: Ctl? = null
        @Volatile var coro: kotlinx.coroutines.Job? = null
        @Volatile var histJob: kotlinx.coroutines.Job? = null
        @Volatile var procId: String? = null
        @Volatile var proc: Process? = null
        @Volatile var token = Any()
        val hist = CopyOnWriteArrayList<Long>()
    }

    private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k, "")

    // ---------- setup ----------
    @Synchronized fun attach(ctx: Context) {
        if (attached) return
        app = ctx.applicationContext; attached = true
        System.setProperty("http.maxConnections", "64")          // keep-alive pool: 32 parallel chunks ek hi socket pool share karein
        scope.launch { runCatching { ensureInit() } }            // preheat: pehli baar Python/yt-dlp unzip + init pehle hi ho jaye
        runCatching {
            val f = File(app.filesDir, "jobs.json")
            if (f.exists()) {
                val a = JSONArray(f.readText())
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val j = Dl(o.getInt("id"), o.getString("url"), o.optInt("height"), o.optLong("size"), o.optString("title"), o.optBoolean("conv"))
                    j.status = o.optString("status", "paused").let { if (it == "running") "paused" else it }
                    j.pct = o.optDouble("pct", 0.0); j.file = o.optString("file"); j.err = o.optString("err")
                    j.elapsedBase = o.optDouble("elapsed", 0.0); j.total = o.optLong("total"); j.dl = o.optLong("dl")
                    jobs[j.id] = j; if (j.id > seq) seq = j.id
                }
            }
        }
    }

    @Synchronized private fun ensureInit() {
        if (inited) return
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
        inited = true
        scope.launch { maybeUpdate() }
    }

    // yt-dlp update har app-start par nahi: 12 ghante mein ek baar, aur pehle kaam (formats) ke baad (20s delay)
    private suspend fun maybeUpdate() {
        val sp = app.getSharedPreferences("vg", 0)
        if (System.currentTimeMillis() - sp.getLong("ytdlp_upd", 0L) < 12 * 3600_000L) return
        delay(20_000)
        if (updateYtDlp()) sp.edit().putLong("ytdlp_upd", System.currentTimeMillis()).apply()
    }

    // yt-dlp ko latest karna (sites roz badalti hain); reflection => library version se compile nahi tootta
    private fun updateYtDlp(): Boolean {
        return runCatching {
            val y = YoutubeDL.getInstance()
            val m = y.javaClass.methods.first { it.name == "updateYoutubeDL" && it.parameterTypes.size == 2 }
            val ch = m.parameterTypes[1]
            var inst: Any? = null
            ch.declaredFields.firstOrNull { it.name.contains("STABLE") }?.let { it.isAccessible = true; inst = it.get(null) }
            if (inst == null) ch.declaredClasses.firstOrNull { it.simpleName.contains("STABLE") }?.let { inst = it.getDeclaredField("INSTANCE").get(null) }
            m.invoke(y, app, inst)
        }.isSuccess
    }

    private fun persist() {
        runCatching {
            val a = JSONArray()
            for (j in jobs.values.sortedBy { it.id }.takeLast(40)) a.put(JSONObject().apply {
                put("id", j.id); put("url", j.url); put("height", j.height); put("size", j.size); put("title", j.title)
                put("status", j.status); put("pct", fin(j.pct)); put("file", j.file); put("err", j.err); put("conv", j.conv)
                put("elapsed", elapsedOf(j).toDouble()); put("total", j.total); put("dl", j.dl)
            })
            File(app.filesDir, "jobs.json").writeText(a.toString())
        }
    }

    private fun keepAlive() {
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, OverlayService::class.java).setAction(OverlayService.ACTION_KEEP))
        }
    }

    private fun notifyDone(text: String) {
        runCatching {
            val nm = app.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel("vgd") == null)
                nm.createNotificationChannel(NotificationChannel("vgd", "Video Grabber - done", NotificationManager.IMPORTANCE_DEFAULT))
            nm.notify(1000 + (System.nanoTime() % 1000).toInt(), Notification.Builder(app, "vgd")
                .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Video Grabber")
                .setContentText(text).setAutoCancel(true).build())
        }
    }

    // ---------- helpers ----------
    private fun fin(d: Double) = if (d.isNaN() || d.isInfinite()) 0.0 else d
    private fun elapsedOf(j: Dl) = Math.round(j.elapsedBase + (if (j.runStart > 0) (System.currentTimeMillis() - j.runStart) / 1000.0 else 0.0))
    private fun settle(j: Dl) { if (j.runStart > 0) { j.elapsedBase += (System.currentTimeMillis() - j.runStart) / 1000.0; j.runStart = 0 } }
    private fun jobDir(j: Dl) = File(app.cacheDir, "vg/${j.id}").apply { mkdirs() }
    private fun partOf(j: Dl, i: Int) = File(jobDir(j), ".vg${j.id}_$i.part")
    private fun cleanDir(j: Dl) { scope.launch { delay(800); runCatching { File(app.cacheDir, "vg/${j.id}").deleteRecursively() } } }
    private fun dirBytes(d: File) = d.listFiles()?.sumOf { it.length() } ?: 0L

    private fun pub(j: Dl) = JSONObject().apply {
        put("dl", j.dl); put("total", j.total); put("elapsed", elapsedOf(j)); put("id", j.id); put("url", j.url)
        put("height", j.height); put("size", j.size); put("title", j.title); put("status", j.status)
        put("pct", fin(j.pct)); put("speed", fin(j.speed)); put("eta", fin(j.eta)); put("file", j.file)
        put("err", j.err); put("merging", j.merging); put("conv", j.conv); put("hist", JSONArray(j.hist.takeLast(60)))
    }

    fun jobsJson(): String { val a = JSONArray(); jobs.values.sortedBy { it.id }.forEach { a.put(pub(it)) }; return a.toString() }
    fun activeCount() = jobs.values.count { it.status == "running" }
    fun summary(): Pair<String, Int> {
        val r = jobs.values.filter { it.status == "running" }
        if (r.isEmpty()) return "Video Grabber active" to -1
        val sp = r.sumOf { it.speed } / 1e6
        return "${r.size} downloading - %.1f MB/s".format(sp) to r.map { it.pct }.average().toInt()
    }

    // ---------- formats (yt-dlp -J) ----------
    private fun fsize(f: JSONObject, dur: Double): Long {
        val a = f.optLong("filesize", 0); if (a > 0) return a
        val b = f.optLong("filesize_approx", 0); if (b > 0) return b
        val tbr = f.optDouble("tbr", 0.0)
        return if (!tbr.isNaN() && tbr > 0 && dur > 0) (tbr * 125 * dur).toLong() else 0L
    }
    private fun chipOf(h: Int): String { for ((t, c) in TIERS) if (h >= t * 0.9) return c; return "LOW" }

    private fun runInfo(url: String, embedded: Boolean): JSONObject {
        val rq = YoutubeDLRequest(url)
        rq.addOption("--no-playlist"); rq.addOption("-J"); rq.addOption("--no-warnings")
        rq.addOption("--socket-timeout", "20"); rq.addOption("--extractor-retries", "2")
        if (embedded) rq.addOption("--extractor-args", EMB)
        val r = YoutubeDL.getInstance().execute(rq, "info-" + System.nanoTime()) { _, _, _ -> }
        val out = r.out
        return JSONObject(out.substring(out.indexOf('{')))
    }

    // age-restricted / "Sign in to confirm your age" => embedded/TV clients se bina login retry
    private const val EMB = "youtube:player_client=tv_embedded,web_embedded,tv"
    private fun ageGate(e: Exception): Boolean {
        val m = (e.message ?: "").lowercase()
        return m.contains("confirm your age") || m.contains("age-restricted") || m.contains("age restricted") ||
            m.contains("sign in to confirm") || m.contains("inappropriate for some users") || m.contains("age verification")
    }

    // "Unable to extract ... update yt-dlp" => site badal gayi, yt-dlp purana: foran update karke ek baar dobara
    private fun stale(e: Exception): Boolean {
        val m = (e.message ?: "").lowercase()
        return m.contains("unable to extract") || m.contains("latest version") || m.contains("please report this issue")
    }
    private val updLock = Any()
    private var lastForced = 0L
    private fun forceUpdate(): Boolean = synchronized(updLock) {
        val now = System.currentTimeMillis()
        if (now - lastForced < 600_000) return false
        lastForced = now
        val ok = updateYtDlp()
        if (ok) app.getSharedPreferences("vg", 0).edit().putLong("ytdlp_upd", now).apply()
        ok
    }

    private fun getInfo(url: String): JSONObject =
        try { runInfo(url, false) } catch (e: Exception) {
            when {
                ageGate(e) -> runInfo(url, true)
                stale(e) && forceUpdate() -> runInfo(url, false)
                else -> throw e
            }
        }

    private fun formatsRaw(url: String): JSONObject {
        ensureInit()
        val info = getInfo(url)
        fmtCache[url] = System.currentTimeMillis() to info
        if (fmtCache.size > 12) fmtCache.keys.firstOrNull { it != url }?.let { fmtCache.remove(it) }
        return info
    }

    private val inflight = HashMap<String, Deferred<JSONObject>>()
    // cache (20 min) + ek hi link ki dohri request ek hi baar chale (prefetch + user click)
    private suspend fun infoFor(url: String): JSONObject {
        fmtCache[url]?.let { if (System.currentTimeMillis() - it.first < 1_200_000) return it.second }
        val d = synchronized(inflight) {
            inflight[url] ?: scope.async {
                try { formatsRaw(url) } finally { synchronized(inflight) { inflight.remove(url) } }
            }.also { inflight[url] = it }
        }
        return d.await()
    }

    @Volatile private var lastPrefetch = 0L
    // Detector jab video page dekhe to qualities pehle se nikal lo => popup foran khulta hai
    fun prefetch(url: String) {
        if (!attached) return
        val now = System.currentTimeMillis()
        if (now - lastPrefetch < 3000) return
        lastPrefetch = now
        scope.launch { runCatching { infoFor(url) } }
    }

    suspend fun formats(url: String): JSONObject = withContext(Dispatchers.IO) { buildTiers(infoFor(url)) }

    private fun buildTiers(info: JSONObject): JSONObject {
        val fa = info.optJSONArray("formats") ?: JSONArray()
        val fm = ArrayList<JSONObject>()
        for (i in 0 until fa.length()) { val f = fa.optJSONObject(i) ?: continue; if (!f.isNull("url") || !f.isNull("format_id")) fm.add(f) }
        val dur = info.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }
        val vids = fm.filter { it.optInt("height", 0) > 0 && it.s("vcodec") != "none" }
        val auds = fm.filter { val a = it.s("acodec"); val v = it.s("vcodec"); a.isNotEmpty() && a != "none" && (v.isEmpty() || v == "none") }
        val aSize = auds.maxOfOrNull { fsize(it, dur) } ?: 0L
        // har alag height ki apni row (900p ke saath 720/480/360 bhi), 8% se kam farq wali heights ek ginte hain
        val byH = vids.groupBy { it.optInt("height") }.toSortedMap(reverseOrder<Int>())
        val tiers = JSONArray()
        var lastKept = 0
        for ((h, g) in byH) {
            if (tiers.length() >= 8) break
            if (lastKept != 0 && h > lastKept * 0.92) continue
            val best = g.maxByOrNull { fsize(it, dur) }!!
            val size = fsize(best, dur) + (if (best.s("acodec") == "none") aSize else 0L)
            tiers.put(JSONObject().put("tier", h).put("height", h).put("chip", chipOf(h)).put("size", size))
            lastKept = h
        }
        if (tiers.length() == 0 && vids.isNotEmpty()) {
            val h = vids.maxOf { it.optInt("height") }
            tiers.put(JSONObject().put("tier", h).put("height", h).put("chip", "SD").put("size", 0))
        }
        if (tiers.length() == 0) tiers.put(JSONObject().put("tier", 0).put("height", 0).put("chip", "BEST").put("size", 0))
        // video mein sirf ek hi quality ho tab bhi 720/480/360 milein: bari quality utar kar phone par chhoti (convert) hoti hai
        if (vids.isNotEmpty() && tiers.getJSONObject(0).optInt("height") > 0) {
            val topH = tiers.getJSONObject(0).optInt("height"); val topSz = tiers.getJSONObject(0).optLong("size")
            for (sH in listOf(720, 480, 360)) {
                if (tiers.length() >= 9) break
                if (sH >= topH * 0.92) continue
                var near = false
                for (i in 0 until tiers.length()) { val hh = tiers.getJSONObject(i).optInt("height"); if (hh >= sH * 0.92 && hh <= sH * 1.08) near = true }
                if (near) continue
                val r = sH.toDouble() / topH
                val est = if (topSz > 0) (topSz * r * r * 0.7).toLong() else 0L
                tiers.put(JSONObject().put("tier", sH).put("height", sH).put("chip", "LITE").put("size", est).put("conv", true))
            }
        }
        val sorted = JSONArray()
        (0 until tiers.length()).map { tiers.getJSONObject(it) }.sortedByDescending { it.optInt("height") }.forEach { sorted.put(it) }
        return JSONObject().put("title", info.optString("title", "video")).put("duration", dur).put("tiers", sorted)
    }

    // LITE ke liye source: S se bari sab se choti quality (kam kaam), warna sab se bari
    private fun srcHeight(info: JSONObject, sH: Int): Int {
        val fa = info.optJSONArray("formats") ?: return 0
        val hs = (0 until fa.length()).mapNotNull { fa.optJSONObject(it) }
            .filter { it.s("vcodec") != "none" }.map { it.optInt("height", 0) }.filter { it > 0 }
        return hs.filter { it >= sH }.minOrNull() ?: hs.maxOrNull() ?: 0
    }

    // ---------- convert (ffmpeg, phone par) ----------
    private fun ffmpegLibDir(): File? = runCatching {
        File(app.noBackupFilesDir, "youtubedl-android").walkTopDown().firstOrNull { it.name == "libavcodec.so" }?.parentFile
    }.getOrNull()

    private fun convert(j: Dl, src: File, dur: Double): File {
        j.merging = true; j.pct = 0.0; j.speed = 0.0; j.eta = 0.0
        val bin = File(app.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val lib = ffmpegLibDir() ?: throw ConvEx("ffmpeg library nahi mili")
        val out = File(src.parentFile, src.nameWithoutExtension + " ${j.height}p.mp4")
        val cmd = listOf(bin.absolutePath, "-y", "-i", src.absolutePath, "-vf", "scale=-2:${j.height}",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "27", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", out.absolutePath)
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = lib.absolutePath + ":" + (System.getenv("LD_LIBRARY_PATH") ?: "")
        val p = try { pb.start() } catch (e: Exception) { throw ConvEx("ffmpeg chal nahi saka: " + (e.message ?: "")) }
        j.proc = p
        val tail = ArrayDeque<String>()
        val rd = Regex("time=(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)")
        p.inputStream.bufferedReader().use { r ->
            val sb = StringBuilder()
            while (true) {
                val c = r.read(); if (c < 0) break
                if (c == 13 || c == 10) {
                    val line = sb.toString(); sb.setLength(0)
                    if (line.isNotBlank()) {
                        tail.addLast(line); if (tail.size > 6) tail.removeFirst()
                        rd.find(line)?.let { m ->
                            val t = m.groupValues[1].toDouble() * 3600 + m.groupValues[2].toDouble() * 60 + m.groupValues[3].toDouble()
                            if (dur > 0) j.pct = minOf(99.0, t * 100.0 / dur)
                        }
                    }
                } else sb.append(c.toChar())
            }
        }
        val code = p.waitFor(); j.proc = null
        if (code != 0 || !out.exists() || out.length() == 0L) {
            out.delete()
            throw ConvEx("Convert fail: " + (tail.lastOrNull() ?: "ffmpeg code $code").take(160))
        }
        src.delete()
        return out
    }

    // ---------- jobs ----------
    fun start(url: String, height: Int, size: Long, title: String, conv: Boolean = false): JSONObject {
        val j = synchronized(this) { val d = Dl(++seq, url, height, size, title, conv); jobs[d.id] = d; d }
        runJob(j)
        return pub(j)
    }

    private fun startHist(j: Dl) {
        j.histJob?.cancel()
        j.histJob = scope.launch {
            while (isActive && j.status == "running") {
                delay(1000); j.hist.add(j.speed.toLong()); while (j.hist.size > 90) j.hist.removeAt(0)
            }
        }
    }

    private fun runJob(j: Dl) {
        j.status = "running"; j.err = ""; j.speed = 0.0; j.merging = false; j.runStart = System.currentTimeMillis()
        jobDir(j); startHist(j); persist(); keepAlive()
        val tok = Any(); j.token = tok
        j.coro = scope.launch {
            try {
                ensureInit()
                if (!j.noTurbo) {
                    try { runTurbo(j, tok); return@launch }
                    catch (e: CancellationException) { throw e }
                    catch (e: ConvEx) { throw e }
                    catch (e: Exception) {
                        if (j.token !== tok || j.status != "running") return@launch
                        j.ctl?.stop = true; j.noTurbo = true; j.td = null; j.dl = 0; j.pct = 0.0; j.merging = false
                        for (i in 0..1) partOf(j, i).delete()
                    }
                }
                runYt(j, tok)
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                if (j.token === tok && j.status == "running") fail(j, e)
            }
        }
    }

    private fun fail(j: Dl, e: Exception) {
        settle(j); j.status = "error"; j.speed = 0.0; j.merging = false
        j.err = (e.message ?: e.toString()).lines().lastOrNull { it.isNotBlank() }?.removePrefix("ERROR: ")?.take(240) ?: "Error"
        persist(); notifyDone("❌ Download fail")
    }

    private fun stopProc(j: Dl) {
        j.ctl?.let { it.stop = true; it.conns.forEach { c -> runCatching { c.disconnect() } } }
        j.procId?.let { id -> runCatching { YoutubeDL.getInstance().destroyProcessById(id) } }
        runCatching { j.proc?.destroy() }
        j.coro?.cancel(); j.histJob?.cancel()
    }

    fun pause(id: Int): Boolean {
        val j = jobs[id] ?: return false; if (j.status != "running") return false
        j.status = "paused"; j.speed = 0.0; settle(j); stopProc(j); persist(); return true
    }
    fun resume(id: Int): Boolean {
        val j = jobs[id] ?: return false; if (j.status !in listOf("paused", "error", "canceled")) return false
        runJob(j); return true
    }
    fun cancel(id: Int): Boolean {
        val j = jobs[id] ?: return false; if (j.status != "running" && j.status != "paused") return false
        val was = j.status; j.status = "canceled"; j.speed = 0.0; settle(j)
        if (was == "running") stopProc(j); j.td = null; cleanDir(j); persist(); return true
    }
    fun remove(id: Int): Boolean {
        val j = jobs[id] ?: return false
        if (j.status == "running") { j.status = "canceled"; stopProc(j) }
        if (j.status != "done") cleanDir(j)
        jobs.remove(id); persist(); return true
    }

    // ---------- TURBO ----------
    private fun pickFormats(info: JSONObject, h: Int): List<JSONObject>? {
        val fa = info.optJSONArray("formats") ?: return null
        val all = (0 until fa.length()).mapNotNull { fa.optJSONObject(it) }
        // googlevideo URL mein clen=<bytes> hota hai => exact size mil jata hai, turbo zyada formats par chalta hai
        for (f in all) if (f.optLong("filesize", 0) <= 0) Regex("[?&]clen=(\\d+)").find(f.s("url"))?.let { f.put("filesize", it.groupValues[1].toLong()) }
        val fm = all.filter {
            !it.isNull("url") && it.s("protocol").let { p -> p == "http" || p == "https" } && it.optLong("filesize", 0) > 0
        }
        val lim = if (h > 0) h else 99999
        val wantH = all.filter { val vc = it.s("vcodec"); vc.isNotEmpty() && vc != "none" && it.optInt("height", 0) in 1..lim }
            .maxOfOrNull { it.optInt("height") } ?: 0
        val vids = fm.filter { val vc = it.s("vcodec"); vc.isNotEmpty() && vc != "none" && it.optInt("height", 0) in 1..lim }
        if (vids.isEmpty()) return null
        val hh = vids.maxOf { it.optInt("height") }
        if (hh < wantH * 0.95) return null                                // chuni hui quality turbo mein nahi => yt-dlp (kam quality chupke se na utre)
        val avc = Regex("^(avc1|h264)")
        fun rank(f: JSONObject) = (if (avc.containsMatchIn(f.s("vcodec"))) 2 else 0) + (if (f.s("ext") == "mp4") 1 else 0)
        val v = vids.filter { it.optInt("height") == hh }
            .sortedWith(compareByDescending<JSONObject> { rank(it) }.thenByDescending { it.optDouble("tbr", 0.0) }).first()
        val ac = v.s("acodec")
        if (ac.isNotEmpty() && ac != "none") return listOf(v)
        if (!avc.containsMatchIn(v.s("vcodec"))) return null          // Android muxer: sirf H.264 + AAC, warna yt-dlp
        val au = fm.filter { val a = it.s("acodec"); val vc = it.s("vcodec"); a.isNotEmpty() && a != "none" && (vc.isEmpty() || vc == "none") && it.s("ext") == "m4a" }
            .maxByOrNull { it.optDouble("abr", it.optDouble("tbr", 0.0)) } ?: return null
        return listOf(v, au)
    }

    private fun getChunk(f: JSONObject, a: Long, b: Long, ctl: Ctl, at: AtomicLong): ByteArray {
        var u = f.getString("url")
        val gv = URL(u).host.endsWith("googlevideo.com", true)
        if (gv) u = if (Regex("[?&]range=").containsMatchIn(u)) u.replace(Regex("([?&])range=[^&]*"), "$1range=$a-$b") else "$u&range=$a-$b"
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 25000; c.useCaches = false
        f.optJSONObject("http_headers")?.let { h -> h.keys().forEach { k -> runCatching { c.setRequestProperty(k, h.getString(k)) } } }
        c.setRequestProperty("Accept-Encoding", "identity")
        if (!gv) c.setRequestProperty("Range", "bytes=$a-$b")
        ctl.conns.add(c)
        var good = false
        try {
            val code = c.responseCode
            if (code != 200 && code != 206) throw IOException("HTTP $code")
            if (!gv && code == 200 && a > 0) throw IOException("server ignores Range")
            val len = (b - a + 1).toInt(); val buf = ByteArray(len); var off = 0
            c.inputStream.use { ins ->
                while (off < len) {
                    val r = ins.read(buf, off, len - off); if (r < 0) break
                    off += r; at.addAndGet(r.toLong()); ctl.bytes.addAndGet(r.toLong())
                }
            }
            if (off != len) throw IOException("short chunk $off")
            good = true
            return buf
        } finally { ctl.conns.remove(c); if (!good) runCatching { c.disconnect() } }   // success par socket pool mein wapas (keep-alive) => har chunk par naya TLS handshake nahi
    }

    private suspend fun turboFile(f: JSONObject, part: File, st: TFile, ctl: Ctl) = coroutineScope {
        val size = f.optLong("filesize"); val n = ((size + CHUNK - 1) / CHUNK).toInt()
        val raf = RandomAccessFile(part, "rw")
        if (!st.sized) { raf.setLength(size); st.sized = true }
        val ch = raf.channel
        val next = AtomicInteger(0)
        try {
            val workers = if (size > 60_000_000) CONC else 8
            (1..minOf(workers, n)).map {
                async(pool) {
                    while (true) {
                        if (ctl.stop) return@async
                        var i = 0
                        while (true) { i = next.getAndIncrement(); if (i >= n) return@async; if (!st.done.contains(i)) break }
                        val a = i.toLong() * CHUNK; val b = minOf(size, a + CHUNK) - 1
                        var ok = false; var err: Exception? = null; var t = 0
                        while (t < 6 && !ctl.stop) {
                            val at = AtomicLong(0)
                            try {
                                val buf = getChunk(f, a, b, ctl, at)
                                ch.write(ByteBuffer.wrap(buf), a); st.done.add(i); ok = true; break
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { ctl.bytes.addAndGet(-at.get()); err = e; delay(400L * (t + 1)) }
                            t++
                        }
                        if (!ok) { if (ctl.stop) return@async; throw err ?: IOException("chunk failed") }
                    }
                }
            }.awaitAll()
        } finally { raf.close() }
    }

    private fun mux(v: File, a: File, out: File) {
        val mm = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val ev = MediaExtractor().apply { setDataSource(v.path) }
        val ea = MediaExtractor().apply { setDataSource(a.path) }
        try {
            fun track(e: MediaExtractor, pre: String): Int {
                for (i in 0 until e.trackCount) if ((e.getTrackFormat(i).getString("mime") ?: "").startsWith(pre)) return i
                throw IOException("no $pre track")
            }
            val vi = track(ev, "video/"); val ai = track(ea, "audio/")
            ev.selectTrack(vi); ea.selectTrack(ai)
            val vt = mm.addTrack(ev.getTrackFormat(vi)); val at = mm.addTrack(ea.getTrackFormat(ai))
            mm.start()
            val buf = ByteBuffer.allocate(4 * 1024 * 1024); val info = MediaCodec.BufferInfo()
            fun copy(e: MediaExtractor, t: Int) {
                while (true) {
                    buf.clear(); val n = e.readSampleData(buf, 0); if (n < 0) break
                    info.set(0, n, e.sampleTime, if (e.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    mm.writeSampleData(t, buf, info); e.advance()
                }
            }
            copy(ev, vt); copy(ea, at)
            mm.stop()
        } finally { runCatching { ev.release() }; runCatching { ea.release() }; runCatching { mm.release() } }
    }

    private suspend fun runTurbo(j: Dl, tok: Any) {
        val info = infoFor(j.url)
        val sel = pickFormats(info, if (j.conv) srcHeight(info, j.height) else j.height) ?: throw IOException("no direct formats")
        val key = sel.joinToString("+") { it.s("format_id") }
        if (j.td == null || j.tdKey != key) { j.tdKey = key; j.td = Array(sel.size) { TFile() } }
        val td = j.td!!
        val ctl = Ctl(); j.ctl = ctl
        val total = sel.sumOf { it.optLong("filesize") }
        sel.forEachIndexed { i, f ->
            val size = f.optLong("filesize")
            for (ix in td[i].done.toList()) ctl.bytes.addAndGet(minOf(size, (ix + 1L) * CHUNK) - ix.toLong() * CHUNK)
        }
        j.total = total
        var last = ctl.bytes.get()
        val timer = scope.launch {
            while (isActive) {
                delay(500)
                val cur = ctl.bytes.get(); val inst = (cur - last) / 0.5; last = cur
                j.speed = if (j.speed > 0) j.speed * 0.6 + inst * 0.4 else inst
                j.dl = maxOf(0L, cur); j.pct = minOf(99.0, j.dl * 100.0 / total)
                j.eta = if (j.speed > 1) (total - j.dl) / j.speed else 0.0
            }
        }
        try {
            coroutineScope { sel.forEachIndexed { i, f -> launch(pool) { turboFile(f, partOf(j, i), td[i], ctl) } } }
        } finally { timer.cancel() }
        if (ctl.stop || j.token !== tok) return
        j.merging = true; j.pct = 99.0; j.speed = 0.0; j.eta = 0.0
        val title = info.optString("title", "video").replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1f]"), "").trim().take(120).ifEmpty { "video" }
        val ext = if (sel.size == 1) sel[0].s("ext").ifEmpty { "mp4" } else "mp4"
        val out = File(jobDir(j), "$title [${info.optString("id", "")}].$ext")
        withContext(Dispatchers.IO) { if (sel.size == 1) { if (!partOf(j, 0).renameTo(out)) throw IOException("rename failed") } else mux(partOf(j, 0), partOf(j, 1), out) }
        val outF = if (j.conv) convert(j, out, info.optDouble("duration", 0.0)) else out
        if (j.token !== tok || j.status != "running") return
        finish(j, outF)
    }

    // ---------- yt-dlp fallback ----------
    private suspend fun runYt(j: Dl, tok: Any) = withContext(Dispatchers.IO) {
        val dir = jobDir(j)
        val h = if (j.conv) srcHeight(infoFor(j.url), j.height) else j.height
        fun req(emb: Boolean): YoutubeDLRequest {
            val rq = YoutubeDLRequest(j.url)
            rq.addOption("--no-playlist"); rq.addOption("--no-warnings")
            rq.addOption("-f", if (h > 0) "bv*[height<=$h]+ba/b[height<=$h]/b" else "bv*+ba/b")
            rq.addOption("-S", "res,vcodec:h264,acodec:m4a")
            rq.addOption("--merge-output-format", "mp4"); rq.addOption("-N", "16")
            rq.addOption("--continue"); rq.addOption("--retries", "15"); rq.addOption("--fragment-retries", "15")
            rq.addOption("--socket-timeout", "30")
            if (emb) rq.addOption("--extractor-args", EMB)
            rq.addOption("-o", dir.absolutePath + "/%(title).120B [%(id)s].%(ext)s")
            return rq
        }
        val pid = "vg-${j.id}-${System.nanoTime()}"; j.procId = pid
        val mon = launch {
            var last = dirBytes(dir)
            while (isActive) {
                delay(500)
                val cur = dirBytes(dir); val inst = (cur - last) / 0.5; last = cur
                if (!j.merging) {
                    j.speed = if (j.speed > 0) j.speed * 0.6 + inst * 0.4 else inst
                    j.dl = cur
                    if (j.size > 0) { j.total = maxOf(j.size, cur); j.pct = minOf(99.0, cur * 100.0 / j.total); if (j.speed > 1) j.eta = (j.total - cur) / j.speed }
                }
            }
        }
        try {
            fun go(emb: Boolean) = YoutubeDL.getInstance().execute(req(emb), pid) { p, eta, line ->
                if (line.contains("[Merger]") || line.contains("[VideoRemuxer]") || line.contains("[ExtractAudio]")) {
                    j.merging = true; j.pct = 99.0; j.speed = 0.0; j.eta = 0.0
                } else if (!j.merging && j.size <= 0) { j.pct = minOf(99.0, p.toDouble().coerceAtLeast(0.0)); j.eta = eta.toDouble().coerceAtLeast(0.0) }
            }
            try { go(false) } catch (e: Exception) { if (j.token === tok && j.status == "running" && ageGate(e)) go(true) else throw e }
        } finally { mon.cancel() }
        if (j.token !== tok || j.status != "running") return@withContext
        val f = dir.listFiles()?.filter { !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.startsWith(".") && it.length() > 0 }
            ?.maxByOrNull { it.length() } ?: throw IOException("File nahi bani")
        val outF = if (j.conv) convert(j, f, infoFor(j.url).optDouble("duration", 0.0)) else f
        if (j.token !== tok || j.status != "running") return@withContext
        finish(j, outF)
    }

    // ---------- save ----------
    private fun finish(j: Dl, f: File) {
        val len = f.length()
        val rel = saveFile(f)
        settle(j); j.file = "$rel/${f.name}"; j.status = "done"; j.pct = 100.0; j.speed = 0.0; j.eta = 0.0
        j.merging = false; j.total = len; j.dl = len
        persist(); cleanDir(j); notifyDone("✅ Saved - $rel")
    }

    private fun saveFile(f: File): String {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "video/mp4"
        val audio = mime.startsWith("audio")
        val rel = if (audio) "Music/VideoGrabber" else "Movies/VideoGrabber"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, f.name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }
            val coll = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = app.contentResolver.insert(coll, cv) ?: throw IOException("MediaStore insert fail")
            app.contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o, 1 shl 20) } }
        } else {
            val d = File(Environment.getExternalStoragePublicDirectory(if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES), "VideoGrabber")
            d.mkdirs(); val t = File(d, f.name); f.copyTo(t, true)
            MediaScannerConnection.scanFile(app, arrayOf(t.absolutePath), null, null)
        }
        return rel
    }
}
