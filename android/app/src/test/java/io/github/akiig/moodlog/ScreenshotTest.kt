package io.github.akiig.moodlog

import android.app.Application
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each screen with sample data, in English and Italian, to build/screenshots/.
 *  Not a phone, but close: used to check the layout and for the README. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class Screens(private val lang: String) {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    companion object {
        fun seed() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val state = AppState.get(app)
            state.prefs.tourDone = true
            state.prefs.customizeSeen = true
            state.chooseDreams(true); state.chooseSeveral("grid")
            state.choosePrivateTick(true); state.choosePrivateDefault(false); state.chooseQuestionnaires(true)
            state.prefs.server = "http://pop-os.local:8765"
            state.prefs.token = "sample-token-0123456789"
            state.prefs.autoSync = false
            if (state.store.entries.isNotEmpty()) return
            val today = Day.today
            val moods = listOf(4, 3, 4, 2, 3, 5, 4, 4, 3, 1, 2, 3, 4, 4, 5, 3, 4, 2, 3, 4)
            val entries = mutableListOf<Entry>()
            moods.forEachIndexed { i, m ->
                val d = Day.adding(-i, today)
                if (Day.date(d)!!.monthValue != Day.date(today)!!.monthValue && i > 18) return@forEachIndexed
                entries.add(Entry("s$i", d, "09:${10 + i}", m, (m % 5) + 1, 6 - m, if (i == 0) "Coffee with a friend, felt lighter" else "", "2026-01-01T00:00:00.000Z"))
                if (i % 4 == 1) entries.add(Entry("t$i", d, "21:05", (m % 5) + 1, 3, 2, "", "2026-01-01T00:00:00.000Z"))
            }
            state.store.absorb(SyncState(entries,
                listOf(MoodEvent("ev1", Day.adding(-9, today), "medication", "New dose", "From 50 to 75 mg", "2026-01-01T00:00:00.000Z")),
                emptyList(),
                listOf(Dream("dr1", Day.adding(-1, today), "pleasant", listOf("vivid"), "Flying over the sea near home, very calm.", "2026-01-01T00:00:00.000Z"),
                       Dream("dr2", Day.adding(-4, today), "disturbing", listOf("nightmare"), "Lost in a station, trains leaving without me.", "2026-01-01T00:00:00.000Z"))))
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/${lang}_$name.png")
    }

    private fun tab(text: String) {
        rule.onAllNodes(hasText(tr(text))).fetchSemanticsNodes().let { }
        rule.onAllNodes(hasText(tr(text)))[0].performClick()
    }

    @Test fun kindWord() {
        val state = AppState.get(ApplicationProvider.getApplicationContext())
        val t = Day.today
        rule.runOnUiThread {
            state.store.entries.filter { Day.between(it.date, t) in 0..2 }.forEach { state.store.delete(it) }
        }
        state.store.absorb(SyncState((0..2).map { Entry("hard$it", Day.adding(-it, t), "07:00", 1, 0, 0, "",
            "2027-01-01T00:00:00.000Z") }, emptyList(), emptyList(), null))
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/${lang}_kind.png")
    }

    @Test fun screens() {
        shot("1-log")
        rule.onAllNodes(hasText(tr("Today"), substring = true)).fetchSemanticsNodes()
        tab("Charts"); shot("2-charts")
        tab("Dreams"); shot("3-dreams")
        tab("Events"); shot("4-events")
        tab("Settings"); shot("5-settings")
        rule.runOnUiThread { AppState.get(ApplicationProvider.getApplicationContext()).tour.start() }
        shot("6-tour-1")
        rule.onAllNodes(hasText(tr("Next")))[0].performClick(); rule.mainClock.advanceTimeBy(2000)
        rule.onAllNodes(hasText(tr("Next")))[0].performClick(); rule.mainClock.advanceTimeBy(2000)
        shot("7-tour-3")
        rule.onAllNodes(hasText(tr("Next")))[0].performClick(); rule.mainClock.advanceTimeBy(2000)
        shot("8-tour-4")
        rule.onAllNodes(hasText(tr("Skip tour")))[0].performClick()
        tab("Log")
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(tr("Today")))[0].performClick()
        shot("9-day")
        rule.onAllNodes(hasText(tr("How do you feel now?")))[0].performClick()
        shot("10-editor")
        report()
    }

    /** The tour's "Customize your experience" step, and the calendar with only the latest mood. */
    @Test fun customize() {
        val state = AppState.get(ApplicationProvider.getApplicationContext())
        rule.runOnUiThread { state.tour.start(); state.tour.step = io.github.akiig.moodlog.ui.Tour.steps.indexOfFirst { it.customize } }
        rule.mainClock.advanceTimeBy(2000)
        shot("customize-tour")
        rule.onAllNodes(hasText(tr("Only the latest")))[0].performScrollTo().performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals("latest", state.prefs.several)
        rule.onAllNodes(hasText(tr("Skip tour")))[0].performScrollTo().performClick()
        rule.runOnUiThread { state.chooseDreams(false) }
        rule.waitForIdle()
        shot("customize-latest")
        org.junit.Assert.assertTrue(rule.onAllNodes(hasText(tr("Dreams"))).fetchSemanticsNodes().isEmpty())
        rule.runOnUiThread { state.chooseDreams(true); state.chooseSeveral("grid") }
    }

    /** Questionnaires: the list with scores over time, answering PHQ-9, and the kind words after question 9. */
    @Test fun surveys() {
        val state = AppState.get(ApplicationProvider.getApplicationContext())
        rule.runOnUiThread { state.chooseQuestionnaires(true) }
        if (state.store.surveys.isEmpty()) {
            val t = Day.today
            val list = listOf(9, 7, 6, 4, 3).flatMapIndexed { i, k ->
                val d = Day.adding(-14 * (4 - i) - 2, t)
                listOf(Survey("q$i", d, "20:00", "phq9", List(9) { j -> ((k + j) % 3).coerceAtMost(k / 3) }, 1, "2026-01-01T00:00:00.000Z"),
                       Survey("a$i", d, "20:05", "gad7", List(7) { j -> ((k + j + 1) % 3).coerceAtMost(k / 3) }, 0, "2026-01-01T00:00:00.000Z"))
            }
            state.store.absorb(SyncState(emptyList(), emptyList(), emptyList(), null, list))
        }
        tab("Settings")
        rule.onAllNodes(hasText(tr("Questionnaires for your doctor")), useUnmergedTree = false)[0].performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)   // a tap could land on the tab bar
        rule.waitForIdle()
        shot("surveys")
        rule.onAllNodes(hasText(tr("Answer %@", "PHQ-9")))[0].performClick()
        rule.waitForIdle()
        shot("surveys-answer")
        rule.onAllNodes(hasText(tr("Cancel")))[0].performClick()
        rule.waitForIdle()
    }

    /** The entry editor with feeling words picked and the day's writing prompt in the empty note. */
    @Test fun feelings() {
        rule.waitForIdle()
        rule.onAllNodes(hasText(tr("Done"))).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
            rule.onAllNodes(hasText(tr("Done")))[0].performClick(); rule.waitForIdle()
        }
        tab("Log")
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(tr("Today")))[0].performClick()
        rule.onAllNodes(hasText(tr("How do you feel now?")))[0].performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasText(tr("Tiredness")))[0].performScrollTo().performClick()
        rule.onAllNodes(hasText(tr("Hope")))[0].performScrollTo().performClick()
        rule.waitForIdle()
        shot("feelings")
        rule.onAllNodes(hasText(tr("Cancel")))[0].performClick()
    }

    /** The search from the diary, with an event found by a word in it. */
    @Test fun search() {
        rule.waitForIdle()
        tab("Log")
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(tr("Search")))[0].performClick()
        rule.waitForIdle()
        shot("search-notes")
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("dose")
        rule.waitForIdle()
        shot("search")
    }

    /** A note ticked private, in the editor and then on the day (for the "more private" video). */
    @Test fun privateNote() {
        tab("Log")
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription(tr("Today")))[0].performClick()
        rule.onAllNodes(hasText(tr("How do you feel now?")))[0].performClick()
        rule.waitForIdle()
        val faces = rule.onAllNodes(hasText(Mood.name(4)))
        faces[faces.fetchSemanticsNodes().size - 1].performClick()
        val note = if (lang == "it") "Una cosa che voglio tenere solo per me." else "Something I want to keep just for me."
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0]
            .performScrollTo().performTextInput(note)
        rule.onAllNodes(hasText(tr("Private note")))[0].performScrollTo().performClick()
        shot("private-editor")
        rule.onAllNodes(hasText(tr("Save")))[0].performClick()
        rule.mainClock.advanceTimeBy(1000)
        shot("private-day")
    }

    /** The doctor report's pages, drawn onto images instead of a PDF. */
    private fun report() {
        val store = AppState.get(ApplicationProvider.getApplicationContext()).store
        val sleep = (0 until 20).associate { Day.adding(-it, Day.today) to listOf(6.2, 7.5, 8.1, 5.4, 7.0)[it % 5] }
        val bitmaps = mutableListOf<android.graphics.Bitmap>()
        Report(store, Day.adding(-29, Day.today), Day.today, includeNotes = true, sleep = sleep,
            includeDreams = true, dreamText = true).draw(object : Report.Pages {
            override fun start(number: Int): android.graphics.Canvas {
                val b = android.graphics.Bitmap.createBitmap(1190, 1684, android.graphics.Bitmap.Config.ARGB_8888)
                bitmaps.add(b)
                return android.graphics.Canvas(b).apply { scale(2f, 2f) }
            }
            override fun end() {}
        })
        java.io.File("build/screenshots").mkdirs()
        bitmaps.forEachIndexed { i, b ->
            java.io.FileOutputStream("build/screenshots/${lang}_report-${i + 1}.png").use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

class ScreensEnglish : Screens("en") {

    init { seed() }
}

@Config(qualifiers = "it-w411dp-h891dp-xxhdpi")
class ScreensItalian : Screens("it") {
    init { seed() }

    @Test fun whatsNew() {
        rule.waitForIdle()
        var tries = 0
        while (rule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().size < 2 && tries++ < 4) {
            rule.onAllNodes(hasText(tr("Settings")))[0].performClick()
            rule.waitForIdle()
            val row = rule.onAllNodes(hasText(tr("What's new")))
            row[row.fetchSemanticsNodes().size - 1].performScrollTo()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            rule.mainClock.advanceTimeBy(1000)
            rule.waitForIdle()
        }
        rule.onAllNodes(androidx.compose.ui.test.isRoot())[1].captureRoboImage("build/screenshots/it_whatsnew.png")
    }
}

/** Large text, as on a phone with a bigger font size in its settings. */
@Config(qualifiers = "it-w392dp-h872dp-xxhdpi", fontScale = 1.3f)
class ScreensLargeText : Screens("it-large") {
    init { seed() }
}
