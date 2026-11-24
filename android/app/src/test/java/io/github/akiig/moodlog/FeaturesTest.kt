package io.github.akiig.moodlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class FeaturesTest {
    private fun store(vararg days: Pair<Int, Int>, today: String = "2026-11-20"): AppStore {
        val s = AppStore(null)
        s.absorb(SyncState(days.mapIndexed { i, (ago, mood) ->
            Entry("e$i", Day.adding(-ago, today), "", mood, 0, 0, "", "2026-01-01T00:00:00.000Z") }, emptyList(), emptyList(), null))
        return s
    }

    @Test fun hardStretch() {
        val t = "2026-11-20"
        assertTrue(Care.hardStretch(store(0 to 1, 1 to 1, 3 to 1), t))
        assertTrue(Care.hardStretch(store(1 to 1, 2 to 2, 2 to 1, 4 to 1), t))      // day 2 averages 1.5
        assertFalse("only two hard days", Care.hardStretch(store(0 to 1, 1 to 1, 2 to 3), t))
        assertFalse("hard days, but not recent", Care.hardStretch(store(2 to 1, 3 to 1, 4 to 1), t))
        assertFalse("bad, not awful", Care.hardStretch(store(0 to 2, 1 to 2, 2 to 2), t))
        assertFalse("spread over more than five days", Care.hardStretch(store(0 to 1, 1 to 1, 6 to 1), t))
    }

    @Test fun reminderTime() {
        val at = LocalTime.of(20, 30)
        val morning = LocalDateTime.of(2026, 11, 20, 9, 0)
        assertEquals(LocalDateTime.of(2026, 11, 20, 20, 30), Reminder.next(morning, at, loggedToday = false))
        assertEquals("already logged: tomorrow", LocalDateTime.of(2026, 11, 21, 20, 30), Reminder.next(morning, at, loggedToday = true))
        assertEquals(LocalDateTime.of(2026, 11, 21, 20, 30), Reminder.next(LocalDateTime.of(2026, 11, 20, 21, 0), at, false))
        assertEquals(LocalTime.of(20, 30), Reminder.parse("nonsense"))
    }

    /** The same numbers _sleep_hours() on the PC gives for these segments. */
    @Test fun sleepMatchesThePc() {
        fun t(s: String) = Instant.parse(s)
        val z = ZoneOffset.ofHours(1)
        val segments = listOf(
            t("2026-11-12T22:40:00Z") to t("2026-11-13T02:10:00Z"),   // 23:40-03:10 local
            t("2026-11-13T02:00:00Z") to t("2026-11-13T05:45:00Z"),   // overlaps the first
            t("2026-11-13T13:00:00Z") to t("2026-11-13T13:40:00Z"),   // an afternoon nap
            t("2026-11-14T00:05:00Z") to t("2026-11-14T07:20:00Z"))
        assertEquals(mapOf("2026-11-13" to 7.75, "2026-11-14" to 7.25), HealthReader.sleepHours(segments, z))
    }

    @Test fun backupRoundTripAndIphoneFile() {
        val s = store(0 to 4, 1 to 2)
        s.addDream("2026-11-19", "pleasant", listOf("vivid"), "sea")
        val text = Backup.content(s)
        val back = Backup.parse(text)
        assertEquals(2, back.entries.size)
        assertEquals("sea", back.dreams!!.single().text)
        val other = AppStore(null)
        assertEquals(3, other.absorb(back))
        assertEquals(0, other.absorb(back))          // restoring twice changes nothing
        // the iPhone app's own data file restores too
        val iphone = """{"entries":[{"id":"i1","date":"2026-11-10","time":"08:00","mood":5,"energy":0,"anxiety":0,"note":"","updated":"2026-11-10T07:00:00Z"}],"events":[],"deleted":[]}"""
        assertEquals(1, Backup.parse(iphone).entries.size)
        try { Backup.parse("""{"something":"else"}"""); throw AssertionError("accepted") } catch (e: IllegalArgumentException) { }
    }

    @Test fun whatsNewWhen() {
        assertEquals(null, WhatsNew.since("", "1.2.0", usedBefore = false))      // new install: the tour instead
        assertEquals("0", WhatsNew.since("", "1.2.0", usedBefore = true))        // from 1.1, which didn't record it
        assertEquals("1.1.0", WhatsNew.since("1.1.0", "1.2.0", usedBefore = true))
        assertEquals(null, WhatsNew.since("1.2.0", "1.2.0", usedBefore = true))
        assertTrue(WhatsNew.newer("1.10.0", "1.9.2"))
        assertFalse(WhatsNew.newer("1.2.0", "1.2.0"))
    }

    @Test fun reportPeriods() {
        val s = store(40 to 3, 0 to 4)
        assertEquals("2026-10-22" to "2026-11-20", ReportPeriod.MONTH.range(s, "2026-11-20"))
        assertEquals("2026-01-01" to "2026-11-20", ReportPeriod.YEAR.range(s, "2026-11-20"))
        assertEquals("2026-10-11" to "2026-11-20", ReportPeriod.ALL.range(s, "2026-11-20"))
    }

    /** Private notes and dreams: "private": true only when set, and it survives a round trip. */
    @Test fun privateFlag() {
        val e = Entry("e1", "2026-11-20", "09:00", 3, 0, 0, "secret", "2026-11-20T08:00:00.000Z", private = true)
        val plain = e.copy(id = "e2", private = false)
        assertEquals(true, e.json().optBoolean("private"))
        assertEquals(false, plain.json().has("private"))
        assertEquals(true, Entry.from(org.json.JSONObject(e.json().toString())).private)
        assertEquals(false, Entry.from(org.json.JSONObject("""{"id":"x","date":"2026-11-20","note":"old"}""")).private)
        val d = Dream("d1", "2026-11-20", "neutral", listOf("vivid"), "text", "2026-11-20T08:00:00.000Z", private = true)
        assertEquals(true, Dream.from(org.json.JSONObject(d.json().toString())).private)
    }

    @Test fun feelingsAndPrompts() {
        assertEquals(18, Feeling.keys.size)
        assertEquals(listOf("joy", "tiredness"), Feeling.clean(listOf("Tiredness", "joy", "nope")))
        val e = Entry("f1", "2026-11-20", "09:00", 2, 0, 0, "", "2026-11-20T08:00:00.000Z", feelings = listOf("worry", "tiredness"))
        assertEquals(listOf("worry", "tiredness"), Entry.from(org.json.JSONObject(e.json().toString())).feelings)
        assertFalse(e.copy(feelings = emptyList()).json().has("feelings"))
        val s = AppStore(null)
        s.absorb(SyncState(listOf(e, e.copy(id = "f2", feelings = listOf("worry"), time = "11:00"), e.copy(id = "f3", private = true, time = "10:00")),
            emptyList(), emptyList(), null))
        assertEquals(listOf("worry" to 2, "tiredness" to 1), Feeling.counts(s, "2026-11-01", "2026-11-30"))
        // the same question on the same day as the PC (Python: date(2026, 11, 21).toordinal() % 14 == 13)
        assertEquals(Prompts.all[13], Prompts.forDay("2026-11-21"))
        assertEquals(Prompts.all[0], Prompts.forDay("2026-11-21", 1))
    }

    @Test fun appointmentAndWeek() {
        val s = AppStore(null)
        fun e(id: String, d: String, mood: Int, f: List<String>) =
            Entry(id, d, "09:00", mood, 0, 0, "", "2026-11-01T00:00:00.000Z", feelings = f)
        s.absorb(SyncState(listOf(e("a", "2026-11-10", 2, listOf("tiredness", "worry")), e("b", "2026-11-11", 2, listOf("tiredness")),
            e("c", "2026-11-12", 4, emptyList())),
            listOf(MoodEvent("v", "2026-11-11", "medication", "New dose", "", "2026-11-01T00:00:00.000Z")), emptyList(), null))
        // the same sentences as the PC's test/test_appointment.py
        assertEquals("Since 5 November: 3 days logged · mostly bad · often Tiredness · 1 event.",
            Gently.recap(s, "2026-11-05", "2026-11-24"))
        assertTrue(Gently.recap(AppStore(null), "2026-11-05", "2026-11-24").contains("no moods"))

        val w = AppStore(null)
        w.absorb(SyncState(listOf(e("1", "2026-11-16", 3, listOf("tiredness")), e("2", "2026-11-17", 4, listOf("tiredness")),
            e("3", "2026-11-19", 5, listOf("joy")), e("4", "2026-11-21", 3, listOf("tiredness"))), emptyList(), emptyList(), null))
        assertNull(Gently.week(w, java.time.LocalDateTime.of(2026, 11, 22, 17, 0)))
        assertEquals("2026-11-16" to "4 days logged, mostly okay. Tiredness came up 3 times. Thursday was your best day.",
            Gently.week(w, java.time.LocalDateTime.of(2026, 11, 22, 19, 0)))
        assertEquals("2026-11-16", Gently.week(w, java.time.LocalDateTime.of(2026, 11, 23, 9, 0))?.first)
        assertNull(Gently.week(w, java.time.LocalDateTime.of(2026, 11, 24, 9, 0)))

        // the card: a week before until the day after
        val appt = Appointment("2026-11-27", "16:30", "Dr Rossi", "2026-11-05", "2026-11-24T08:00:00.000Z")
        assertTrue(Gently.cardShown(appt, "2026-11-24") && Gently.cardShown(appt, "2026-11-28"))
        assertFalse(Gently.cardShown(appt, "2026-11-19") || Gently.cardShown(appt, "2026-11-29"))
        assertEquals("2026-11-05", Gently.recapStart(appt, "2026-11-24"))
        assertTrue(Gently.whenText(appt, "2026-11-24").endsWith("at 16:30, with Dr Rossi · in 3 days"))

        // sync: a newer appointment wins, points merge, a deleted point stays deleted
        val a = AppStore(null)
        a.addPoint("  The  new dose makes me  sleepy ")
        a.addPoint("Ask about therapy")
        assertEquals("The new dose makes me sleepy", a.points[0].text)
        val phone = AppStore(null)
        phone.absorb(SyncState.from(a.toJson()))
        assertEquals(2, phone.points.size)
        phone.absorb(SyncState(emptyList(), emptyList(), listOf(Tombstone(a.points[0].id, "point", "2999-01-01T00:00:00.000Z", null)),
            null, appointment = appt.copy(updated = "2999-01-01T00:00:00.000Z")))
        assertEquals(2, a.absorb(SyncState.from(phone.toJson())))
        assertEquals(listOf("Ask about therapy"), a.points.map { it.text })
        assertEquals("16:30", a.appointment?.time)
        assertEquals(0, a.absorb(SyncState(emptyList(), emptyList(), emptyList(), null,
            points = listOf(TalkPoint(phone.deleted.first { it.type == "point" }.id, "old", false, "2026-01-01T00:00:00.000Z")))))
        a.setDone(a.points[0], true)
        assertTrue(a.openPoints.isEmpty())
    }

    @Test fun appointmentReminder() {
        val a = Appointment("2026-11-27", "16:30", "Dr Rossi", "", "2026-11-24T08:00:00.000Z")
        assertEquals(java.time.LocalDateTime.of(2026, 11, 20, 18, 0), AppointmentReminder.fireAt(a, 7))
        assertEquals(java.time.LocalDateTime.of(2026, 11, 26, 18, 0), AppointmentReminder.fireAt(a, 1))
        assertEquals(java.time.LocalDateTime.of(2026, 11, 27, 8, 0), AppointmentReminder.fireAt(a, 0))
        // an early appointment: an hour before instead of 8:00
        assertEquals(java.time.LocalDateTime.of(2026, 11, 27, 7, 30), AppointmentReminder.fireAt(a.copy(time = "08:30"), 0))
        assertEquals("Your appointment is tomorrow at 16:30. 2 things to talk about.", AppointmentReminder.text(a, 1, 2))
        assertEquals("Your appointment is today.", AppointmentReminder.text(a.copy(time = ""), 0, 0))
        assertTrue(AppointmentReminder.text(a, 3, 1).startsWith("Your appointment is in 3 days, on "))
        assertFalse(AppointmentReminder.text(a, 1, 0).contains("Rossi"))   // never says with whom
        assertEquals(listOf("1 week before", "3 days before", "2 days before", "1 day before", "On the day"),
            AppointmentReminder.LEADS.map { AppointmentReminder.label(it) })
    }

    @Test fun search() {
        assertEquals("perche ansia", Search.fold("Perché ÀNSIA"))
        assertEquals(listOf(0 until 6, 11 until 17), Search.find("Perché no? perche sì", "PERCHE"))
        assertTrue(Search.find("anything", "  ").isEmpty())
        val walk = Entry("a", "2026-11-01", "09:00", 4, 0, 0, "Long walk by the sea", "2026-11-01T08:00:00.000Z")
        val tired = Entry("b", "2026-11-02", "08:00", 2, 0, 0, "", "2026-11-02T08:00:00.000Z", feelings = listOf("tiredness"))
        val late = Entry("c", "2026-11-02", "22:00", 3, 0, 0, "Walked home, tired", "2026-11-02T08:00:00.000Z")
        val blank = Entry("d", "2026-11-03", "10:00", 3, 0, 0, "", "2026-11-03T08:00:00.000Z")
        val exam = MoodEvent("e", "2026-11-03", "life", "Exam", "Maths, went better than expected", "2026-11-03T08:00:00.000Z")
        val entries = listOf(walk, tired, late, blank)
        fun ids(q: String) = Search.results(entries, listOf(exam), q).map { it.key }
        // the same answers as the PC's test/test_search.py
        assertEquals(listOf("entry:c", "entry:a"), ids(""))
        assertEquals(listOf("entry:c", "entry:a"), ids("WALK"))
        assertEquals(listOf("entry:c", "entry:b"), ids("tired"))
        assertEquals(listOf("event:e"), ids("maths"))
        assertTrue(ids("nothing like this").isEmpty())
    }

    @Test fun surveys() {
        assertEquals(9, SurveyText.italian.forms.getValue(SurveyKind.PHQ9).items.size)
        assertEquals(7, SurveyText.english.forms.getValue(SurveyKind.GAD7).items.size)
        val s = AppStore(null)
        val phq = Survey("p1", "2026-11-01", "20:00", "phq9", listOf(1, 1, 0, 0, 0, 0, 0, 0, 1), 1, "2026-11-01T19:00:00.000Z")
        val gad = Survey("g1", "2026-11-01", "20:05", "gad7", listOf(0, 1, 2, 3, 0, 0, 0), -1, "2026-11-01T19:05:00.000Z")
        val bad = Survey("b1", "2026-11-01", "", "phq9", listOf(0, 0), -1, "2026-11-01T19:00:00.000Z")
        val doc = org.json.JSONObject().put("entries", org.json.JSONArray()).put("events", org.json.JSONArray())
            .put("deleted", org.json.JSONArray()).put("surveys", org.json.JSONArray(listOf(phq, gad, bad).map { it.json() }))
        assertEquals(2, s.absorb(SyncState.from(doc)))
        assertEquals(listOf("p1", "g1"), s.surveys.map { it.id })
        assertTrue(s.surveys[0].worries)
        assertEquals(3, s.surveys[0].score)
        // an older PC sends no surveys: ours stay
        assertEquals(0, s.absorb(SyncState(emptyList(), emptyList(), emptyList(), null)))
        assertEquals(2, s.surveys.size)
        // a deletion from the PC removes it
        assertEquals(1, s.absorb(SyncState(emptyList(), emptyList(),
            listOf(Tombstone("g1", "survey", "2099-01-01T00:00:00.000Z", null)), null)))
        assertEquals(listOf("p1"), s.surveys.map { it.id })
        assertTrue(s.toJson().getJSONArray("surveys").length() == 1)
        // the reminder: 14 days after the last one at 19:00, then weekly
        assertEquals(LocalDateTime.of(2026, 11, 15, 19, 0),
            SurveyReminder.next("2026-11-01", LocalDateTime.of(2026, 11, 10, 9, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 22, 19, 0),
            SurveyReminder.next("2026-11-01", LocalDateTime.of(2026, 11, 16, 9, 0)))
        assertEquals(null, SurveyReminder.next(null))
    }
}
