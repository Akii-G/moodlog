package io.github.akiig.moodlog

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// The same records Moodlog on the PC keeps. Field names match the PC's /api/e/sync JSON,
// and the saved file is the same as the iPhone app's.

data class Entry(
    val id: String,
    val date: String,      // "YYYY-MM-DD"
    val time: String,      // "HH:MM" or "" when no time was given
    val mood: Int,         // 1-5, 0 = not given
    val energy: Int,       // 1-5, 0 = not given
    val anxiety: Int,      // 1-5, 0 = not given
    val note: String,
    val updated: String,   // UTC change time, e.g. 2026-11-19T05:20:31.123Z
    val private: Boolean = false,   // never in the doctor report
    val feelings: List<String> = emptyList(),   // feeling words, e.g. "joy", "tiredness"
    val activities: List<String> = emptyList(), // activity tags, e.g. "work", "walk"
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("date", date).put("time", time)
        .put("mood", mood).put("energy", energy).put("anxiety", anxiety).put("note", note)
        .put("updated", updated).also { if (private) it.put("private", true) }
        .also { if (feelings.isNotEmpty()) it.put("feelings", JSONArray(feelings)) }
        .also { if (activities.isNotEmpty()) it.put("activities", JSONArray(activities)) }

    fun sameContent(o: Entry) = date == o.date && time == o.time && mood == o.mood &&
        energy == o.energy && anxiety == o.anxiety && note == o.note

    companion object {
        fun from(o: JSONObject) = Entry(o.getString("id"), o.getString("date"), o.optString("time"),
            o.optInt("mood"), o.optInt("energy"), o.optInt("anxiety"), o.optString("note"),
            o.optString("updated"), o.optBoolean("private", false),
            o.optJSONArray("feelings")?.let { a -> Feeling.clean((0 until a.length()).map { a.optString(it) }) } ?: emptyList(),
            o.optJSONArray("activities")?.let { a -> Activity.clean((0 until a.length()).map { a.optString(it) }) } ?: emptyList())
    }
}

data class MoodEvent(
    val id: String,
    val date: String,
    val kind: String,      // medication, health, life, other
    val title: String,
    val note: String,
    val updated: String,
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("date", date).put("kind", kind)
        .put("title", title).put("note", note).put("updated", updated)

    fun sameContent(o: MoodEvent) = date == o.date && kind == o.kind && title == o.title && note == o.note

    companion object {
        fun from(o: JSONObject) = MoodEvent(o.getString("id"), o.getString("date"),
            o.optString("kind", "other"), o.optString("title"), o.optString("note"), o.optString("updated"))
    }
}

/** A dream journal entry. `date` is the morning you woke up. */
data class Dream(
    val id: String,
    val date: String,
    val feeling: String,   // "", pleasant, neutral, disturbing
    val tags: List<String>, // nightmare, vivid, recurring, lucid, forgotten
    val text: String,
    val updated: String,
    val private: Boolean = false,   // counted in the doctor report, its text never shown
) {
    val isNightmare get() = "nightmare" in tags || feeling == "disturbing"

    /** Feeling and tags as one line: "Disturbing · Nightmare, Vivid". */
    val details: String
        get() {
            val parts = mutableListOf<String>()
            DreamFeeling.of(feeling)?.let { parts.add(it.label) }
            val names = DreamTag.entries.filter { it.raw in tags }.map { it.label }
            if (names.isNotEmpty()) parts.add(names.joinToString(", "))
            return parts.joinToString(" · ")
        }

    fun json(): JSONObject = JSONObject().put("id", id).put("date", date).put("feeling", feeling)
        .put("tags", JSONArray(tags)).put("text", text).put("updated", updated)
        .also { if (private) it.put("private", true) }

    fun sameContent(o: Dream) = date == o.date && feeling == o.feeling && tags == o.tags && text == o.text

    companion object {
        fun from(o: JSONObject): Dream {
            val arr = o.optJSONArray("tags") ?: JSONArray()
            return Dream(o.getString("id"), o.getString("date"), o.optString("feeling"),
                (0 until arr.length()).map { arr.getString(it) }, o.optString("text"), o.optString("updated"),
                o.optBoolean("private", false))
        }
    }
}

enum class DreamFeeling(val raw: String) {
    PLEASANT("pleasant"), NEUTRAL("neutral"), DISTURBING("disturbing");

    val label: String
        get() = when (this) {
            PLEASANT -> tr("Pleasant")
            NEUTRAL -> tr("Neutral")
            DISTURBING -> tr("Disturbing")
        }

    companion object {
        fun of(raw: String) = entries.firstOrNull { it.raw == raw }
    }
}

enum class DreamTag(val raw: String) {
    NIGHTMARE("nightmare"), VIVID("vivid"), RECURRING("recurring"), LUCID("lucid"), FORGOTTEN("forgotten");

    val label: String
        get() = when (this) {
            NIGHTMARE -> tr("Nightmare")
            VIVID -> tr("Vivid")
            RECURRING -> tr("Recurring")
            LUCID -> tr("Lucid")
            FORGOTTEN -> tr("Didn't remember")
        }
}

data class Tombstone(val id: String, val type: String, val deleted: String, val date: String?) {
    fun json(): JSONObject = JSONObject().put("id", id).put("type", type).put("deleted", deleted)
        .also { if (date != null) it.put("date", date) }

    companion object {
        fun from(o: JSONObject) = Tombstone(o.getString("id"), o.optString("type", "entry"),
            o.optString("deleted"), if (o.has("date") && !o.isNull("date")) o.getString("date") else null)
    }
}

/** What the PC sends back. `dreams` is null when the PC runs Moodlog older than 2.3. */
data class SyncState(
    val entries: List<Entry>,
    val events: List<MoodEvent>,
    val deleted: List<Tombstone>,
    val dreams: List<Dream>?,
    val surveys: List<Survey>? = null,   // null when the PC runs Moodlog older than 2.10
    val appointment: Appointment? = null,
    val points: List<TalkPoint>? = null,   // null when the PC runs Sottovoce older than 3.0.2
    val dose: DoseFeed? = null,   // Dose's medication, from a PC with Dose installed
) {
    companion object {
        fun from(o: JSONObject): SyncState {
            fun <T> list(key: String, make: (JSONObject) -> T): List<T> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { make(arr.getJSONObject(it)) }
            }
            return SyncState(list("entries", Entry::from), list("events", MoodEvent::from),
                list("deleted", Tombstone::from),
                if (o.has("dreams")) list("dreams", Dream::from) else null,
                if (o.has("surveys")) list("surveys", Survey::from) else null,
                Appointment.from(o.optJSONObject("appointment")),
                if (o.has("points")) list("points", TalkPoint::from) else null,
                DoseFeed.from(o.optJSONObject("dose")))
        }
    }
}

object Mood {
    val names = mapOf(1 to "Awful", 2 to "Bad", 3 to "Okay", 4 to "Good", 5 to "Great")

    fun name(value: Int): String = names[value]?.let { tr(it) } ?: ""

    /** Average mood -> nearest whole level, halves round up (same as the PC). */
    fun level(average: Double): Int = (average + 0.5).toInt().coerceIn(1, 5)
}

object Scale {
    val energyWords get() = mapOf(1 to tr("very low"), 2 to tr("low"), 3 to tr("medium"),
        4 to tr("high"), 5 to tr("very high"))
    val anxietyWords get() = mapOf(1 to tr("calm"), 2 to tr("a little"), 3 to tr("some"),
        4 to tr("a lot"), 5 to tr("very anxious"))
}

enum class EventKind(val raw: String) {
    MEDICATION("medication"), HEALTH("health"), LIFE("life"), OTHER("other");

    val label: String
        get() = when (this) {
            MEDICATION -> tr("Medication change")
            HEALTH -> tr("Health")
            LIFE -> tr("Life event")
            OTHER -> tr("Other")
        }

    companion object {
        fun label(raw: String) = (entries.firstOrNull { it.raw == raw } ?: OTHER).label
    }
}

object Stamp {
    private val f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    fun now(): String = f.format(Instant.now())
}

/** Days are "YYYY-MM-DD" strings, as on the PC, so they sort and compare as text. */
object Day {
    val today: String get() = LocalDate.now().toString()

    fun date(day: String): LocalDate? = try { LocalDate.parse(day) } catch (e: Exception) { null }

    fun adding(days: Int, day: String): String = date(day)?.plusDays(days.toLong())?.toString() ?: day

    fun timeString(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    fun now(): String = timeString(LocalTime.now())

    private fun cap(s: String) = s.replaceFirstChar { it.titlecase(appLocale()) }

    /** "Monday 16 November", in the app's language ("Lunedì 16 novembre"). */
    fun pretty(day: String, pattern: String = "EEEE d MMMM"): String {
        val d = date(day) ?: return day
        return cap(DateTimeFormatter.ofPattern(pattern, appLocale()).format(d))
    }

    /** M T W … S in the app's language, Monday first. */
    val weekdayInitials: List<String>
        get() = (1..7).map { cap(java.time.DayOfWeek.of(it).getDisplayName(TextStyle.SHORT_STANDALONE, appLocale())) }

    fun monthStart(d: LocalDate = LocalDate.now()): LocalDate = d.withDayOfMonth(1)

    fun days(month: LocalDate): List<String> =
        (1..month.lengthOfMonth()).map { month.withDayOfMonth(it).toString() }

    /** Cells for a Monday-first month grid; null = empty cell before the 1st. */
    fun grid(month: LocalDate): List<String?> {
        val lead = month.withDayOfMonth(1).dayOfWeek.value - 1
        return List(lead) { null } + days(month)
    }

    fun monthTitle(month: LocalDate): String =
        cap(DateTimeFormatter.ofPattern("LLLL yyyy", appLocale()).format(month))

    fun between(a: String, b: String): Long {
        val x = date(a) ?: return 0
        val y = date(b) ?: return 0
        return ChronoUnit.DAYS.between(x, y)
    }
}

fun one(value: Double): String = String.format(Locale.US, "%.1f", value)

/**
 * Medication from Dose, the sister app, sent by Sottovoce on the PC with each sync (see
 * dose_to_json in src/moodlog). Only read here: Dose keeps the medicines and the doses.
 * A "pending" dose is scheduled and nobody marked it: not taken once its time has passed,
 * otherwise yet to take.
 */
data class DoseFeed(val medicines: List<Med>, val doses: List<Item>) {
    data class Med(val name: String, val archived: Boolean, val asNeeded: Boolean)
    data class Item(val date: String, val time: String, val status: String, val amount: String, val name: String) {
        fun missed(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): Boolean {
            if (status != "pending") return false
            val today = now.toLocalDate().toString()
            if (date != today) return date < today
            return time.isNotEmpty() && time <= now.toLocalTime().toString().take(5)
        }
        val statusText: String get() = when (status) {
            "taken" -> tr("Taken")
            "skipped" -> tr("Skipped")
            else -> if (missed()) tr("Not taken") else tr("Yet to take")
        }
    }

    fun on(day: String) = doses.filter { it.date == day }.sortedWith(compareBy({ it.time }, { it.name.lowercase() }))
    fun between(start: String, end: String) = doses.filter { it.date in start..end }

    fun json(): JSONObject = JSONObject()
        .put("medicines", JSONArray(medicines.map { JSONObject().put("name", it.name).put("archived", it.archived).put("asNeeded", it.asNeeded) }))
        .put("doses", JSONArray(doses.map { JSONObject().put("date", it.date).put("time", it.time).put("status", it.status)
            .put("amount", it.amount).put("name", it.name) }))

    companion object {
        fun from(o: JSONObject?): DoseFeed? {
            o ?: return null
            val meds = o.optJSONArray("medicines") ?: return null
            val doses = o.optJSONArray("doses") ?: JSONArray()
            return DoseFeed(
                (0 until meds.length()).mapNotNull { meds.optJSONObject(it) }.map {
                    Med(it.optString("name"), it.optBoolean("archived"), it.optBoolean("asNeeded")) }.filter { it.name.isNotEmpty() },
                (0 until doses.length()).mapNotNull { doses.optJSONObject(it) }.map {
                    Item(it.optString("date"), it.optString("time"), it.optString("status"), it.optString("amount"), it.optString("name"))
                }.filter { it.name.isNotEmpty() && it.date.length == 10 && it.status in setOf("taken", "skipped", "pending") })
        }
    }
}
