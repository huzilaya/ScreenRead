package com.example.screenread

import android.content.Context

object Prefs {
    private const val FILE = "screen_read_prefs"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun showPanel(c: Context) = sp(c).getBoolean("show_panel", true)
    fun setShowPanel(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_panel", v).apply()

    fun autoCopy(c: Context) = sp(c).getBoolean("auto_copy", false)
    fun setAutoCopy(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto_copy", v).apply()

    fun rules(c: Context): String =
        sp(c).getString("rules", "广告,上滑\n抽奖,下滑") ?: ""

    fun setRules(c: Context, v: String) = sp(c).edit().putString("rules", v).apply()

    fun rulesEnabled(c: Context) = sp(c).getBoolean("rules_enabled", true)
    fun setRulesEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("rules_enabled", v).apply()

    fun intervalSec(c: Context) = sp(c).getInt("interval", 3)
    fun setIntervalSec(c: Context, v: Int) = sp(c).edit().putInt("interval", v).apply()
}
