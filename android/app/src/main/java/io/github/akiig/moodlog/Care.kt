package io.github.akiig.moodlog

/**
 * The kind word on hard stretches: after several hard days close together, the Log screen
 * shows a small, calm card with a helpline. Never an alarm, never a judgement; it can be
 * dismissed (then it waits a week) or turned off in Settings.
 */
object Care {
    /** Three logged days with an average of 1.75 or lower ("Awful", or nearly) within the
     *  last five days, the latest of them today or yesterday. */
    fun hardStretch(store: AppStore, today: String = Day.today): Boolean {
        val days = (0 until 5).map { Day.adding(-it, today) }
        val hard = days.filter { d -> store.average(d)?.let { it <= 1.75 } ?: false }
        return hard.size >= 3 && (today in hard || Day.adding(-1, today) in hard)
    }

    fun shouldShow(store: AppStore, prefs: Prefs, today: String = Day.today): Boolean {
        if (!prefs.kindWord) return false
        val dismissed = prefs.kindDismissed
        if (dismissed.isNotEmpty() && Day.between(dismissed, today) < 7) return false
        return hardStretch(store, today)
    }

    data class Helpline(val name: String, val number: String?, val link: String?, val note: String)

    /** Who to call, in the app's language. Checked on 20 November 2026 (PRIVACY.md lists
     *  the sources): Telefono Amico Italia 02 2327 2327, WhatsApp 324 011 7252; 112 is the
     *  emergency number across Europe; findahelpline.com lists 1,400+ helplines worldwide. */
    fun helplines(): List<Helpline> = if (appLocale().language == "it") listOf(
        Helpline("Telefono Amico Italia", "02 2327 2327", null, tr("Every day, free and anonymous. Also on WhatsApp: 324 011 7252.")),
        Helpline(tr("Emergency"), "112", null, tr("If you're in danger right now.")),
    ) else listOf(
        Helpline("Find A Helpline", null, "https://findahelpline.com", tr("Free, confidential helplines in your country.")),
        Helpline(tr("Emergency"), "112", null, tr("If you're in danger right now: 112 in Europe, or your local emergency number.")),
    )
}
