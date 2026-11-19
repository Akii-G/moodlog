package io.github.akiig.moodlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The merge rules are the PC's: newest change wins, deletions win over older changes,
 *  identical items made on two devices aren't duplicated, and the file survives a restart. */
class AppStoreTest {
    private fun e(id: String, mood: Int, updated: String, note: String = "") =
        Entry(id, "2026-11-10", "09:00", mood, 0, 0, note, updated)

    @Test fun newestChangeWins() {
        val s = AppStore(null)
        s.absorb(SyncState(listOf(e("a", 2, "2026-11-10T09:00:00.000Z")), emptyList(), emptyList(), emptyList()))
        val changes = s.absorb(SyncState(listOf(e("a", 4, "2026-11-10T10:00:00.000Z")), emptyList(), emptyList(), emptyList()))
        assertEquals(1, changes)
        assertEquals(4, s.entries.single().mood)
        assertEquals(0, s.absorb(SyncState(listOf(e("a", 1, "2026-11-10T08:00:00.000Z")), emptyList(), emptyList(), emptyList())))
        assertEquals(4, s.entries.single().mood)
    }

    @Test fun deletionWinsOverOlderChange() {
        val s = AppStore(null)
        s.absorb(SyncState(listOf(e("a", 2, "2026-11-10T09:00:00.000Z")), emptyList(), emptyList(), null))
        s.absorb(SyncState(emptyList(), emptyList(), listOf(Tombstone("a", "entry", "2026-11-10T09:30:00.000Z", "2026-11-10")), null))
        assertTrue(s.entries.isEmpty())
        // the PC still sending the old version doesn't bring it back
        s.absorb(SyncState(listOf(e("a", 2, "2026-11-10T09:00:00.000Z")), emptyList(), emptyList(), null))
        assertTrue(s.entries.isEmpty())
    }

    @Test fun identicalContentIsNotDuplicated() {
        val s = AppStore(null)
        s.absorb(SyncState(listOf(e("a", 3, "2026-11-10T09:00:00.000Z", "same")), emptyList(), emptyList(), null))
        assertEquals(0, s.absorb(SyncState(listOf(e("b", 3, "2026-11-10T09:05:00.000Z", "same")), emptyList(), emptyList(), null)))
        assertEquals(1, s.entries.size)
    }

    @Test fun olderPcWithoutDreamsKeepsOurs() {
        val s = AppStore(null)
        s.absorb(SyncState(emptyList(), emptyList(), emptyList(),
            listOf(Dream("d", "2026-11-10", "pleasant", listOf("vivid"), "flying", "2026-11-10T07:00:00.000Z"))))
        s.absorb(SyncState(emptyList(), emptyList(), emptyList(), null))
        assertEquals("flying", s.dreams.single().text)
    }

    @Test fun savesAndLoadsTheSameFileAsTheIphone() {
        val f = File.createTempFile("moodlog", ".json")
        f.writeText("""{"entries":[{"id":"x1","date":"2026-11-10","time":"","mood":5,"energy":2,"anxiety":0,
            "note":"è ok","updated":"2026-11-10T09:00:00.000Z"}],"events":[],"deleted":[]}""")
        val s = AppStore(f)
        assertEquals("è ok", s.entries.single().note)
        assertTrue(s.dreams.isEmpty())                      // files from iPhone 1.1 have no dreams
        s.addDream("2026-11-11", "", listOf("forgotten"), "")
        val again = AppStore(f)
        assertEquals(listOf("forgotten"), again.dreams.single().tags)
        assertEquals(1, again.entries.size)
    }

    @Test fun moodAroundAndStreak() {
        val s = AppStore(null)
        val today = Day.today
        val list = (0 until 5).map { Entry("e$it", Day.adding(-it, today), "", 4, 0, 0, "", "2026-01-01T00:00:00.000Z") }
        s.absorb(SyncState(list, emptyList(), emptyList(), null))
        assertEquals(5, s.streak)
        val around = s.moodAround(Day.adding(-2, today))
        assertEquals(2, around.before.days)
        assertEquals(3, around.after.days)
    }
}
