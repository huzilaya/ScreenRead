package com.example.screenread

import android.content.Context

object Prefs {
    private const val FILE = "screen_read_prefs"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun showPanel(c: Context) = sp(c).getBoolean("show_panel", true)
    fun setShowPanel(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_panel", v).apply()

    fun autoCopy(c: Context) = sp(c).getBoolean("auto_copy", false)
    fun setAutoCopy(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto_copy", v).apply()
}
