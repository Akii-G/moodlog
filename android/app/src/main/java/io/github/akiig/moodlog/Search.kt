package io.github.akiig.moodlog

import java.text.Normalizer

/**
 * The search, the same as on the PC: the words in notes, the feeling words and events, ignoring
 * capitals and accents ("perche" finds "Perché"). With no words, every note, newest first.
 */
object Search {
    sealed class Hit {
        abstract val date: String
        abstract val key: String
        data class OfEntry(val entry: Entry) : Hit() {
            override val date get() = entry.date
            override val key get() = "entry:" + entry.id
        }
        data class OfEvent(val event: MoodEvent) : Hit() {
            override val date get() = event.date
            override val key get() = "event:" + event.id
        }
        /** Newest first; an event comes before the moods of its day, like on the PC. */
        val order: String get() = when (this) {
            is OfEntry -> entry.date + " " + entry.time.ifEmpty { "00:00" }
            is OfEvent -> event.date + " 99:99"
        }
    }

    private val marks = Regex("\\p{Mn}+")

    fun fold(text: String): String = marks.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")

    /** Where the query appears in the text, as (start, end) ranges of the original text. */
    fun find(text: String, query: String): List<IntRange> {
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList()
        val folded = StringBuilder()
        val where = ArrayList<Int>()        // where[i]: the original character folded[i] came from
        for ((i, c) in text.withIndex()) {
            val f = fold(c.toString())
            folded.append(f)
            repeat(f.length) { where.add(i) }
        }
        val out = ArrayList<IntRange>()
        var start = folded.indexOf(q)
        while (start >= 0) {
            val end = start + q.length
            out.add(where[start] until where[end - 1] + 1)
            start = folded.indexOf(q, end)
        }
        return out
    }

    fun matches(text: String, query: String) = find(text, query).isNotEmpty()

    fun results(entries: List<Entry>, events: List<MoodEvent>, query: String): List<Hit> {
        val q = query.trim()
        val hits = ArrayList<Hit>()
        for (e in entries) {
            if (q.isEmpty()) {
                if (e.note.isNotBlank()) hits.add(Hit.OfEntry(e))
            } else if (matches(e.note, q) || matches(Feeling.text(e.feelings), q) || matches(Activity.text(e.activities), q)) {
                hits.add(Hit.OfEntry(e))
            }
        }
        if (q.isNotEmpty()) for (ev in events) if (matches(ev.title, q) || matches(ev.note, q)) hits.add(Hit.OfEvent(ev))
        return hits.sortedByDescending { it.order }
    }
}
