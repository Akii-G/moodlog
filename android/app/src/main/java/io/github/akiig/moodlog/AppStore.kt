package io.github.akiig.moodlog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * All entries, events and dreams on this phone, saved as one JSON file (the same format as
 * the iPhone app). Merging follows the same rules as Moodlog on the PC: for each item the
 * newest change wins, a deletion wins over changes made before it, and identical items
 * created separately on two devices are not duplicated.
 */
class AppStore(private val file: File?) {
    var entries by mutableStateOf(listOf<Entry>())
        private set
    var events by mutableStateOf(listOf<MoodEvent>())
        private set
    var dreams by mutableStateOf(listOf<Dream>())
        private set
    var deleted by mutableStateOf(listOf<Tombstone>())
        private set

    var onChange: (() -> Unit)? = null

    init {
        load()
    }

    fun load() {
        val f = file ?: return
        if (!f.exists()) return
        try {
            val s = SyncState.from(JSONObject(f.readText()))
            entries = s.entries
            events = s.events
            dreams = s.dreams ?: emptyList()
            deleted = s.deleted
        } catch (e: Exception) {
            // an unreadable file is kept as it is, not overwritten with nothing
            f.copyTo(File(f.path + ".unreadable"), overwrite = true)
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("entries", JSONArray(entries.map { it.json() }))
        .put("events", JSONArray(events.map { it.json() }))
        .put("deleted", JSONArray(deleted.map { it.json() }))
        .put("dreams", JSONArray(dreams.map { it.json() }))

    fun save() {
        val f = file ?: return
        val tmp = File(f.path + ".tmp")
        tmp.writeText(toJson().toString())
        tmp.renameTo(f)
    }

    private fun changed() {
        save()
        onChange?.invoke()
    }

    // ---------------------------------------------------------------- reading

    fun entriesOn(day: String) = entries.filter { it.date == day }.sortedBy { it.time }

    fun moodsOn(day: String) = entriesOn(day).map { it.mood }.filter { it > 0 }

    fun average(day: String): Double? = moodsOn(day).takeIf { it.isNotEmpty() }?.average()

    fun scaleAverage(day: String, energy: Boolean): Double? =
        entriesOn(day).map { if (energy) it.energy else it.anxiety }.filter { it > 0 }
            .takeIf { it.isNotEmpty() }?.average()

    fun eventsOn(day: String) = events.filter { it.date == day }

    fun dreamsOn(day: String) = dreams.filter { it.date == day }

    /** Days in a row with a mood, up to today (or yesterday if today is still empty). */
    val streak: Int
        get() {
            var day = Day.today
            if (average(day) == null) day = Day.adding(-1, day)
            var count = 0
            while (average(day) != null) {
                count++
                day = Day.adding(-1, day)
            }
            return count
        }

    data class Window(val average: Double, val days: Int)
    data class Around(val before: Window, val after: Window, val complete: Boolean)

    /** Average mood in the 14 days before an event and from the event day on. */
    fun moodAround(day: String, span: Int = 14): Around {
        fun window(from: String, to: String): Window {
            val values = mutableListOf<Double>()
            var d = from
            while (d <= to) {
                average(d)?.let { values.add(it) }
                d = Day.adding(1, d)
            }
            return if (values.isEmpty()) Window(0.0, 0) else Window(values.average(), values.size)
        }
        val before = window(Day.adding(-span, day), Day.adding(-1, day))
        val afterEnd = Day.adding(span - 1, day)
        val after = window(day, minOf(afterEnd, Day.today))
        return Around(before, after, afterEnd <= Day.today)
    }

    /** Dream counts for a period, and the mood on days after a disturbing dream vs other days. */
    fun dreamSummary(start: String, end: String): String? {
        val items = dreams.filter { it.date in start..end }
        val nights = items.map { it.date }.toSet()
        if (nights.isEmpty()) return null
        val bad = items.filter { it.isNightmare }.map { it.date }.toSet()
        val pleasant = items.filter { it.feeling == "pleasant" }.map { it.date }.toSet()
        val parts = mutableListOf(trn("%@ night with a dream entry", "%@ nights with a dream entry", nights.size))
        if (bad.isNotEmpty()) parts.add(trn("%@ with a nightmare or a disturbing dream",
            "%@ with a nightmare or a disturbing dream", bad.size))
        if (pleasant.isNotEmpty()) parts.add(trn("%@ pleasant one", "%@ pleasant ones", pleasant.size))
        var text = parts.joinToString(", ") + "."
        val afterBad = bad.mapNotNull { average(it) }
        val afterOther = (nights - bad).mapNotNull { average(it) }
        if (afterBad.size >= 3 && afterOther.size >= 3) {
            text += " " + tr("Your mood on days after a disturbing dream: %@; after other dreams: %@. This shows a pattern in your data, not a cause.",
                one(afterBad.average()), one(afterOther.average()))
        }
        return text
    }

    // ---------------------------------------------------------------- changing

    fun addEntry(date: String, time: String, mood: Int, energy: Int, anxiety: Int, note: String) {
        entries = entries + Entry(newId(), date, time, mood, energy, anxiety, note, Stamp.now())
        changed()
    }

    fun update(entry: Entry) {
        val copy = entry.copy(updated = Stamp.now())
        entries = entries.map { if (it.id == copy.id) copy else it }
        changed()
    }

    fun delete(entry: Entry) {
        entries = entries.filter { it.id != entry.id }
        deleted = deleted + Tombstone(entry.id, "entry", Stamp.now(), entry.date)
        changed()
    }

    fun addEvent(date: String, kind: String, title: String, note: String) {
        events = events + MoodEvent(newId(), date, kind, title, note, Stamp.now())
        changed()
    }

    fun update(event: MoodEvent) {
        val copy = event.copy(updated = Stamp.now())
        events = events.map { if (it.id == copy.id) copy else it }
        changed()
    }

    fun delete(event: MoodEvent) {
        events = events.filter { it.id != event.id }
        deleted = deleted + Tombstone(event.id, "event", Stamp.now(), event.date)
        changed()
    }

    fun addDream(date: String, feeling: String, tags: List<String>, text: String) {
        dreams = dreams + Dream(newId(), date, feeling, tags, text, Stamp.now())
        changed()
    }

    fun update(dream: Dream) {
        val copy = dream.copy(updated = Stamp.now())
        dreams = dreams.map { if (it.id == copy.id) copy else it }
        changed()
    }

    fun delete(dream: Dream) {
        dreams = dreams.filter { it.id != dream.id }
        deleted = deleted + Tombstone(dream.id, "dream", Stamp.now(), dream.date)
        changed()
    }

    // ---------------------------------------------------------------- sync

    /** Merge what the PC sent. Returns how many things changed here. */
    fun absorb(other: SyncState): Int {
        var changes = 0
        val tombs = HashMap<String, Tombstone>()
        for (t in deleted + other.deleted) {
            val old = tombs[t.id]
            if (old != null && old.deleted >= t.deleted) continue
            tombs[t.id] = t
        }

        fun <T> merge(mine: List<T>, theirs: List<T>?, type: String, id: (T) -> String,
                      updated: (T) -> String, same: (T, T) -> Boolean): List<T> {
            val byId = LinkedHashMap<String, T>()
            for (x in mine) {
                val old = byId[id(x)]
                if (old != null && updated(old) >= updated(x)) continue
                byId[id(x)] = x
            }
            for (x in theirs ?: emptyList()) {   // an older PC sends no dreams: keep ours
                val t = tombs[id(x)]
                if (t != null && t.deleted >= updated(x)) continue
                val have = byId[id(x)]
                if (have != null) {
                    if (updated(x) > updated(have)) {
                        byId[id(x)] = x; changes++
                    }
                } else if (byId.values.any { same(it, x) }) {
                    continue
                } else {
                    byId[id(x)] = x; changes++
                }
            }
            for (t in tombs.values) {
                if (t.type != type) continue
                val have = byId[t.id] ?: continue
                if (updated(have) <= t.deleted) {
                    byId.remove(t.id); changes++
                }
            }
            return byId.values.toList()
        }

        entries = merge(entries, other.entries, "entry", { it.id }, { it.updated }, { a, b -> a.sameContent(b) })
        events = merge(events, other.events, "event", { it.id }, { it.updated }, { a, b -> a.sameContent(b) })
            .sortedBy { it.date }
        dreams = merge(dreams, other.dreams, "dream", { it.id }, { it.updated }, { a, b -> a.sameContent(b) })
            .sortedBy { it.date }
        deleted = tombs.values.toList()
        save()
        return changes
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)
    }
}
