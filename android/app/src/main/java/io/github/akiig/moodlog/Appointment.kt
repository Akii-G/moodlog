package io.github.akiig.moodlog

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * "Before the appointment": the next doctor or therapist appointment and the things to talk
 * about there. Synced with the PC like the rest: the appointment as a whole and each point on
 * its own, newest change wins; deleted points are kept as tombstones (type "point").
 */
data class Appointment(
    val date: String,       // "YYYY-MM-DD", "" when there is none
    val time: String,       // "HH:MM" or ""
    val with: String,       // "Dr Rossi" or ""
    val previous: String,   // the appointment before, where the recap starts; "" when unknown
    val updated: String,
) {
    fun json(): JSONObject = JSONObject().put("date", date).put("time", time).put("with", with)
        .put("previous", previous).put("updated", updated)

    companion object {
        private val timeRe = Regex("([01]\\d|2[0-3]):[0-5]\\d")

        fun from(o: JSONObject?): Appointment? {
            if (o == null) return null
            val updated = o.optString("updated")
            if (updated.isEmpty()) return null
            fun day(key: String) = o.optString(key).takeIf { Day.date(it) != null } ?: ""
            val time = o.optString("time").takeIf { timeRe.matches(it) } ?: ""
            return Appointment(day("date"), time, o.optString("with").split(Regex("\\s+")).filter { it.isNotEmpty() }
                .joinToString(" ").take(200), day("previous"), updated)
        }
    }
}

data class TalkPoint(val id: String, val text: String, val done: Boolean, val updated: String) {
    val isValid get() = id.isNotEmpty() && updated.isNotEmpty() && text.isNotBlank()

    fun json(): JSONObject = JSONObject().put("id", id).put("text", text).put("done", done).put("updated", updated)

    companion object {
        fun from(o: JSONObject) = TalkPoint(o.optString("id"), o.optString("text").trim(),
            o.optBoolean("done", false), o.optString("updated"))
    }
}

/** The calm summaries: the recap before an appointment and "Your week, gently". The same rules
 *  and words as the PC and the iPhone. */
object Gently {
    /** The appointment card in the diary: from a week before until the day after. */
    fun cardShown(a: Appointment?, today: String = Day.today): Boolean {
        if (a == null || a.date.isEmpty()) return false
        val days = Day.between(today, a.date)
        return days in -1..7
    }

    /** Where "since the last appointment" starts: the previous one, or 30 days ago. */
    fun recapStart(a: Appointment?, today: String = Day.today): String {
        val prev = a?.previous ?: ""
        return if (prev.isNotEmpty() && prev < today) prev else Day.adding(-29, today)
    }

    /** "Friday 27 November at 16:30, with Dr Rossi · in 3 days" */
    fun whenText(a: Appointment, today: String = Day.today): String {
        var text = Day.pretty(a.date, "EEEE d MMMM")
        if (a.time.isNotEmpty()) text = tr("%@ at %@", text, a.time)
        if (a.with.isNotEmpty()) text = tr("%@, with %@", text, a.with)
        val days = Day.between(today, a.date).toInt()
        val rel = when {
            days == 0 -> tr("today")
            days == 1 -> tr("tomorrow")
            days == -1 -> tr("yesterday")
            days > 0 -> trn("in %@ day", "in %@ days", days)
            else -> trn("%@ day ago", "%@ days ago", -days)
        }
        return "$text · $rel"
    }

    private fun usual(moods: List<Int>): Int =
        (1..5).maxWithOrNull(compareBy({ m -> moods.count { it == m } }, { it })) ?: 3

    fun recap(store: AppStore, start: String, end: String): String {
        val since = Day.pretty(start, "d MMMM")
        val inRange = store.entries.filter { it.date in start..end }
        val logged = inRange.filter { it.mood > 0 }.map { it.date }.toSet()
        if (logged.isEmpty()) return tr("Since %@: no moods logged yet.", since)
        val parts = mutableListOf(trn("%@ day logged", "%@ days logged", logged.size))
        parts.add(tr("mostly %@", Mood.name(usual(inRange.map { it.mood }.filter { it > 0 })).lowercase()))
        val top = Feeling.counts(store, start, end).take(2).filter { it.second > 1 }.map { Feeling.label(it.first) }
        if (top.isNotEmpty()) parts.add(tr("often %@", top.joinToString(", ")))
        val events = store.events.count { it.date in start..end }
        if (events > 0) parts.add(trn("%@ event", "%@ events", events))
        return tr("Since %@: %@.", since, parts.joinToString(" · "))
    }

    /** From Sunday 18:00 until the end of Monday: a few plain sentences about the week (Monday
     *  to Sunday) that is ending or just ended. null when there is nothing to say. */
    fun week(store: AppStore, now: LocalDateTime = LocalDateTime.now()): Pair<String, String>? {
        val today = now.toLocalDate().toString()
        val monday = when {
            now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18 -> Day.adding(-6, today)
            now.dayOfWeek == DayOfWeek.MONDAY -> Day.adding(-7, today)
            else -> return null
        }
        val sunday = Day.adding(6, monday)
        val days = (0..6).map { Day.adding(it, monday) }
        val logged = days.filter { store.average(it) != null }
        if (logged.isEmpty()) return null
        val moods = store.entries.filter { it.date in monday..sunday && it.mood > 0 }.map { it.mood }
        val lines = mutableListOf(trn("%@ day logged, mostly %@.", "%@ days logged, mostly %@.", logged.size,
            Mood.name(usual(moods)).lowercase()))
        val top = Feeling.counts(store, monday, sunday).firstOrNull()
        if (top != null && top.second > 1) lines.add(tr("%@ came up %@ times.", Feeling.label(top.first), top.second))
        if (logged.size >= 2) {
            val avgs = logged.map { it to (store.average(it) ?: 0.0) }
            val best = avgs.maxWithOrNull(compareBy({ it.second }, { it.first }))!!
            if (best.second > avgs.minOf { it.second }) lines.add(tr("%@ was your best day.", Day.pretty(best.first, "EEEE")))
        }
        return monday to lines.joinToString(" ")
    }
}
