package io.github.akiig.moodlog

import org.junit.Assert.assertEquals
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
}
