package io.github.akiig.moodlog

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deleted nights and renamed medicines: the same rules as Corrections in src/moodlog. */
class CorrectionsTest {
    private val feed = DoseFeed(
        listOf(DoseFeed.Med("Aripiprazolo Teva 15 mg", false, false, "Aripiprazolo"),
            DoseFeed.Med("Abilify 15 mg", true, false, "Aripiprazolo"),
            DoseFeed.Med("Depas 1 mg", false, true, "Etizolam")),
        listOf(DoseFeed.Item("2026-11-20", "08:00", "taken", "1 pill", "Abilify 15 mg"),
            DoseFeed.Item("2026-11-25", "08:00", "taken", "1 pill", "Aripiprazolo Teva 15 mg"),
            DoseFeed.Item("2026-11-25", "", "taken", "1 pill", "Depas 1 mg")))

    @Test fun renamesAndSuggestions() {
        val names = feed.doses.map { it.name }.toSet()
        val groups = Corrections.suggestions(names, feed.medicines.associate { it.name to it.ingredient }, emptyMap())
        assertEquals(listOf(listOf("Abilify 15 mg", "Aripiprazolo Teva 15 mg")), groups)   // same active ingredient
        val c = Corrections().rename(groups[0], "Aripiprazolo")
        val shown = feed.renamed(c.renames())
        assertEquals(setOf("Aripiprazolo", "Depas 1 mg"), shown.doses.map { it.name }.toSet())
        assertFalse("one name still in use keeps it current", shown.medicines.first { it.name == "Aripiprazolo" }.archived)
        assertTrue(Corrections.suggestions(names, emptyMap(), c.renames()).isEmpty())
        // renamed again: the old names follow
        val again = c.rename(listOf("Aripiprazolo"), "Aripiprazolo (Abilify)")
        assertEquals("Aripiprazolo (Abilify)", again.renames()["Abilify 15 mg"])
        assertTrue(again.undoRename("Aripiprazolo (Abilify)").renames().isEmpty())
    }

    @Test fun syncMerge() {
        val mine = Corrections().deleteNight("2026-11-11")
        val pc = Corrections.from(JSONObject("""{"nights": {"2026-11-22": "2026-11-26T18:00:00.000Z"},
            "names": {"Etizolam": {"to": "Depas 1 mg", "updated": "2026-11-26T18:00:00.000Z"}}}"""))!!
        val (merged, n) = mine.absorb(pc)
        assertEquals(2, n)
        assertEquals(setOf("2026-11-11", "2026-11-22"), merged.deletedNights)
        // an older undo doesn't win over a newer rename
        val old = Corrections(names = mapOf("Etizolam" to Corrections.Rename("", "2026-01-01T00:00:00.000Z")))
        assertEquals(0, merged.absorb(old).second)
        // and it survives the store's file and the trip to the PC
        val s = AppStore(null)
        s.absorb(SyncState(emptyList(), emptyList(), emptyList(), null, corrections = merged))
        val back = Corrections.from(s.toJson().getJSONObject("corrections"))!!
        assertEquals(merged.deletedNights, back.deletedNights)
        assertEquals("Depas 1 mg", back.renames()["Etizolam"])
    }
}
