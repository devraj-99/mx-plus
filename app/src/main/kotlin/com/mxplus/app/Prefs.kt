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
    var glassStrength by mutableFloatStateOf(1f) // refraction strength for lens glass
    var glassBlur by mutableFloatStateOf(24f) // blur radius (dp) for haze glass
    var glassOpacity by mutableFloatStateOf(0.42f) // fill/tint opacity for glass surfaces
    var playerGlass by mutableStateOf(true) // blur glass over the video
    var seekSeconds by mutableIntStateOf(10)
    var autoHideSec by mutableIntStateOf(4) // 0 = never hide
    var resume by mutableStateOf(true)
    var gestures by mutableStateOf(true)
    var keepOn by mutableStateOf(true)
    var autoNext by mutableStateOf(true)
    var defaultSpeed by mutableFloatStateOf(1f)
    var subScale by mutableFloatStateOf(1f) // subtitle text scale
    var subColor by mutableIntStateOf(0) // 0 white, 1 yellow, 2 green, 3 cyan
    var loopMode by mutableIntStateOf(0) // 0 off, 1 all, 2 one
    var bgPlay by mutableStateOf(false)
    var volumeBoost by mutableIntStateOf(100) // percent 100..200
    var eqEnabled by mutableStateOf(false)
    var eqPreset by mutableIntStateOf(0)
    var bright by mutableIntStateOf(0) // -50..50
    var contrast by mutableIntStateOf(100) // 50..200 percent
    var saturate by mutableIntStateOf(100) // 0..200 percent
    var hue by mutableIntStateOf(0) // -180..180

    fun init(context: Context) {
        if (sp != null) return
        val s = context.applicationContext.getSharedPreferences("mxplus_settings", Context.MODE_PRIVATE)
        sp = s
        resumeSp = context.applicationContext.getSharedPreferences("mxplus_resume", Context.MODE_PRIVATE)
        theme = s.getInt("theme", 0)
        accent = s.getInt("accent", 0)
        glass = s.getBoolean("glass", true)
        glassStrength = s.getFloat("glassStrength", 1f)
        glassBlur = s.getFloat("glassBlur", 24f)
        glassOpacity = s.getFloat("glassOpacity", 0.42f)
        playerGlass = s.getBoolean("playerGlass", true)
        seekSeconds = s.getInt("seek", 10)
        autoHideSec = s.getInt("autoHide", 4)
        resume = s.getBoolean("resume", true)
        gestures = s.getBoolean("gestures", true)
        keepOn = s.getBoolean("keepOn", true)
        autoNext = s.getBoolean("autoNext", true)
        defaultSpeed = s.getFloat("speed", 1f)
        subScale = s.getFloat("subScale", 1f)
        subColor = s.getInt("subColor", 0)
        loopMode = s.getInt("loopMode", 0)
        bgPlay = s.getBoolean("bgPlay", false)
        volumeBoost = s.getInt("volBoost", 100)
        eqEnabled = s.getBoolean("eqOn", false)
        eqPreset = s.getInt("eqPreset", 0)
        bright = s.getInt("bright", 0)
        contrast = s.getInt("contrast", 100)
        saturate = s.getInt("sat", 100)
        hue = s.getInt("hue", 0)
    }

    fun save() {
        sp?.edit()
            ?.putInt("theme", theme)
            ?.putInt("accent", accent)
            ?.putBoolean("glass", glass)
            ?.putFloat("glassStrength", glassStrength)
            ?.putFloat("glassBlur", glassBlur)
            ?.putFloat("glassOpacity", glassOpacity)
            ?.putBoolean("playerGlass", playerGlass)
            ?.putInt("seek", seekSeconds)
            ?.putInt("autoHide", autoHideSec)
            ?.putBoolean("resume", resume)
            ?.putBoolean("gestures", gestures)
            ?.putBoolean("keepOn", keepOn)
            ?.putBoolean("autoNext", autoNext)
            ?.putFloat("speed", defaultSpeed)
            ?.putFloat("subScale", subScale)
            ?.putInt("subColor", subColor)
            ?.putInt("loopMode", loopMode)
            ?.putBoolean("bgPlay", bgPlay)
            ?.putInt("volBoost", volumeBoost)
            ?.putBoolean("eqOn", eqEnabled)
            ?.putInt("eqPreset", eqPreset)
            ?.putInt("bright", bright)
            ?.putInt("contrast", contrast)
            ?.putInt("sat", saturate)
            ?.putInt("hue", hue)
            ?.apply()
    }

    fun resumePosition(uri: String): Long = resumeSp?.getLong(uri, 0L) ?: 0L

    fun saveResume(uri: String, positionMs: Long, durationMs: Long) {
        val e = resumeSp?.edit() ?: return
        if (positionMs < 5_000L || (durationMs > 0 && positionMs > durationMs - 8_000L)) e.remove(uri) else e.putLong(uri, positionMs)
        e.apply()
    }
}
