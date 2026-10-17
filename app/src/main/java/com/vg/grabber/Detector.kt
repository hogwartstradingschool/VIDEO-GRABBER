package com.vg.grabber

import android.os.Handler
import android.os.Looper

object Detector {
    @Volatile var url: String? = null
    @Volatile var pkg: String? = null
    var listener: (() -> Unit)? = null

    private val hosts = listOf("youtube.com", "youtu.be", "facebook.com", "fb.watch", "instagram.com",
        "tiktok.com", "twitter.com", "x.com", "vimeo.com", "dailymotion.com", "reddit.com", "twitch.tv",
        "bilibili.com", "pinterest.com", "linkedin.com", "rumble.com", "vk.com", "ok.ru", "snapchat.com")

    fun isKnown(u: String) = hosts.any { u.contains(it, ignoreCase = true) }

    private val full = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val bare = Regex("""^[\w.-]+\.[a-z]{2,}/\S+$""", RegexOption.IGNORE_CASE)

    fun normalize(raw: String?, allowBare: Boolean): String? {
        val s = raw?.trim() ?: return null
        full.find(s)?.let { return it.value }
        if (allowBare && bare.matches(s)) return "https://$s"
        return null
    }

    fun update(p: String, u: String?) {
        if (p == pkg && u == url) return
        pkg = p; url = u
        Handler(Looper.getMainLooper()).post { listener?.invoke() }
    }
}
