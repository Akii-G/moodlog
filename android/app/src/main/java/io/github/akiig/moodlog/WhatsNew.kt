package io.github.akiig.moodlog

import android.content.Context
import org.json.JSONArray

/**
 * "What's new": this app's changelog (assets/changelog.json, made by tools/phone_changelog.py
 * from src/moodlog), shown once after an update and from Settings.
 */
object WhatsNew {
    data class Version(val version: String, val date: String, val items: List<String>)

    fun load(context: Context): List<Version> = try {
        val arr = JSONArray(context.assets.open("changelog.json").bufferedReader().use { it.readText() })
        val lang = if (appLocale().language == "it") "it" else "en"
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val items = o.getJSONObject("items").let { it.optJSONArray(lang) ?: it.getJSONArray("en") }
            Version(o.getString("version"), o.getString("date"), (0 until items.length()).map { items.getString(it) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** "1.10.0" > "1.9.2". */
    fun key(v: String): List<Int> = v.split('.').map { it.toIntOrNull() ?: 0 }

    fun newer(a: String, b: String): Boolean {
        val x = key(a); val y = key(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /**
     * The version last seen, from which to mark what's new, or null when nothing should be
     * shown: a brand-new install (it gets the tour) or no change. An update from a version
     * that didn't record this yet counts as coming from the previous version.
     */
    fun since(lastSeen: String, current: String, usedBefore: Boolean): String? = when {
        lastSeen.isEmpty() && !usedBefore -> null
        lastSeen.isEmpty() -> "0"
        newer(current, lastSeen) -> lastSeen
        else -> null
    }
}
