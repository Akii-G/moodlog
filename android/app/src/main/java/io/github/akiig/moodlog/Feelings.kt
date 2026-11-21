package io.github.akiig.moodlog

import java.time.LocalDate

/**
 * Optional words next to a mood, more precise than five faces. Nouns, so they read the same for
 * everyone in Italian too. The keys are stored and synced; the same list in every app.
 */
object Feeling {
    val keys = listOf("joy", "calm", "gratitude", "pride", "hope", "relief", "motivation", "connection", "sadness", "worry", "tiredness", "loneliness", "irritation", "overwhelm", "emptiness", "guilt", "boredom", "stress")

    fun label(key: String): String = when (key) {
        "joy" -> tr("Joy")
        "calm" -> tr("Calm")
        "gratitude" -> tr("Gratitude")
        "pride" -> tr("Pride")
        "hope" -> tr("Hope")
        "relief" -> tr("Relief")
        "motivation" -> tr("Motivation")
        "connection" -> tr("Connection")
        "sadness" -> tr("Sadness")
        "worry" -> tr("Worry")
        "tiredness" -> tr("Tiredness")
        "loneliness" -> tr("Loneliness")
        "irritation" -> tr("Irritation")
        "overwhelm" -> tr("Overwhelm")
        "emptiness" -> tr("Emptiness")
        "guilt" -> tr("Guilt")
        "boredom" -> tr("Boredom")
        "stress" -> tr("Stress")
        else -> key
    }

    /** Known keys only, in the list's order. */
    fun clean(values: List<String>): List<String> {
        val wanted = values.map { it.lowercase() }.toSet()
        return keys.filter { it in wanted }
    }

    fun text(values: List<String>): String = clean(values).joinToString(", ") { label(it) }

    /** (key, how many entries) in a period, most often first; private entries left out. */
    fun counts(store: AppStore, start: String, end: String): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        for (e in store.entries) if (e.date in start..end && !e.private) for (k in clean(e.feelings)) counts[k] = (counts[k] ?: 0) + 1
        return counts.entries.sortedWith(compareBy({ -it.value }, { keys.indexOf(it.key) })).map { it.key to it.value }
    }
}

/**
 * Writing prompts: a gentle question when the note is empty. One a day, or another one when
 * asked. The same list, in the same order, in every app.
 */
object Prompts {
    val all get() = listOf(
        tr("What took most of your energy today?"),
        tr("What's one small thing that went well?"),
        tr("Is there something you're carrying that you'd like to put down here?"),
        tr("Who did you talk to today, and how did it feel?"),
        tr("What would you like to tell yourself tonight?"),
        tr("How did you sleep, and how did it shape the day?"),
        tr("What made you smile, even a little?"),
        tr("What's on your mind right now?"),
        tr("What did your body need today?"),
        tr("Was there a moment you felt like yourself?"),
        tr("What's worrying you, in a few words?"),
        tr("What are you looking forward to, even something small?"),
        tr("What would make tomorrow a bit easier?"),
        tr("What did you do just for you today?"),
    )

    /** The day's question (the same as on the PC: Python's toordinal); offset moves to the next ones. */
    fun forDay(day: String, offset: Int = 0): String {
        val d = Day.date(day) ?: LocalDate.now()
        val ordinal = d.toEpochDay() + 719_163L
        val n = all.size
        return all[(((ordinal + offset) % n + n) % n).toInt()]
    }
}
