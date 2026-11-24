package com.mxplus.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Prefs {
    private var sp: SharedPreferences? = null
    private var resumeSp: SharedPreferences? = null

    var theme by mutableIntStateOf(0) // 0 system, 1 dark, 2 light
    var accent by mutableIntStateOf(0)
    var glass by mutableStateOf(true)
    var glassStrength by mutableFloatStateOf(1f)
    var seekSeconds by mutableIntStateOf(10)
    var resume by mutableStateOf(true)
    var gestures by mutableStateOf(true)
    var keepOn by mutableStateOf(true)
    var autoNext by mutableStateOf(true)
    var defaultSpeed by mutableFloatStateOf(1f)

    fun init(context: Context) {
        if (sp != null) return
        val s = context.applicationContext.getSharedPreferences("mxplus_settings", Context.MODE_PRIVATE)
        sp = s
        resumeSp = context.applicationContext.getSharedPreferences("mxplus_resume", Context.MODE_PRIVATE)
        theme = s.getInt("theme", 0)
        accent = s.getInt("accent", 0)
        glass = s.getBoolean("glass", true)
        glassStrength = s.getFloat("glassStrength", 1f)
        seekSeconds = s.getInt("seek", 10)
        resume = s.getBoolean("resume", true)
        gestures = s.getBoolean("gestures", true)
        keepOn = s.getBoolean("keepOn", true)
        autoNext = s.getBoolean("autoNext", true)
        defaultSpeed = s.getFloat("speed", 1f)
    }

    fun save() {
        sp?.edit()
            ?.putInt("theme", theme)
            ?.putInt("accent", accent)
            ?.putBoolean("glass", glass)
            ?.putFloat("glassStrength", glassStrength)
            ?.putInt("seek", seekSeconds)
            ?.putBoolean("resume", resume)
            ?.putBoolean("gestures", gestures)
            ?.putBoolean("keepOn", keepOn)
            ?.putBoolean("autoNext", autoNext)
            ?.putFloat("speed", defaultSpeed)
            ?.apply()
    }

    fun resumePosition(uri: String): Long = resumeSp?.getLong(uri, 0L) ?: 0L

    fun saveResume(uri: String, positionMs: Long, durationMs: Long) {
        val e = resumeSp?.edit() ?: return
        if (positionMs < 5_000L || (durationMs > 0 && positionMs > durationMs - 8_000L)) e.remove(uri) else e.putLong(uri, positionMs)
        e.apply()
    }
}
