package com.vg.grabber

import android.content.Context

object Prefs {
    fun on(c: Context) = c.getSharedPreferences("vg", 0).getBoolean("bubble", true)
    fun set(c: Context, v: Boolean) = c.getSharedPreferences("vg", 0).edit().putBoolean("bubble", v).apply()
}
