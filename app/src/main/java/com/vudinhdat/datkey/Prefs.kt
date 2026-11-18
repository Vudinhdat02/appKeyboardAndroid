package com.vudinhdat.datkey

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("datkey", Context.MODE_PRIVATE)

    var vietnamese: Boolean
        get() = sp.getBoolean("vietnamese", true)
        set(v) = sp.edit().putBoolean("vietnamese", v).apply()

    val vni get() = sp.getString("method", "telex") == "vni"
    val modernTone get() = sp.getBoolean("modern_tone", true)
    val autoCap get() = sp.getBoolean("auto_cap", true)
    val autoCorrect get() = sp.getBoolean("auto_correct", true)
    val predictive get() = sp.getBoolean("predictive", true)
    val capsLock get() = sp.getBoolean("caps_lock", true)
    val doubleSpacePeriod get() = sp.getBoolean("double_space", true)
    val smartPunct get() = sp.getBoolean("smart_punct", true)
    val sound get() = sp.getBoolean("sound", true)
    val haptic get() = sp.getBoolean("haptic", true)
    val numberRow get() = sp.getBoolean("number_row", false)
    val showGlobe get() = sp.getBoolean("show_globe", true)
    val showEmoji get() = sp.getBoolean("show_emoji", true)
    val preview get() = sp.getBoolean("preview", true)
    val learn get() = sp.getBoolean("learn", true)
    val color get() = sp.getString("color", "default") ?: "default"
    val theme get() = sp.getString("theme", "system") ?: "system"
    val heightScale get() = sp.getInt("height", 100).coerceIn(70, 150) / 100f

    var recentEmoji: List<String>
        get() = sp.getString("recent_emoji", "")!!.split('\u0001').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("recent_emoji", v.joinToString("\u0001")).apply()

    var shortcuts: Map<String, String>
        get() = runCatching {
            val o = JSONObject(sp.getString("shortcuts", DEFAULT_SHORTCUTS)!!)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(v) = sp.edit().putString("shortcuts", JSONObject(v).toString()).apply()

    companion object {
        const val DEFAULT_SHORTCUTS = """{"omw":"On my way!","tks":"Cảm ơn bạn nhiều!"}"""
    }
}
