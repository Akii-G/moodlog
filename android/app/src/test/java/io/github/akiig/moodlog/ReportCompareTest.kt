package io.github.akiig.moodlog

import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws the doctor report from a shared fixture (REPORT_FIXTURE=dir with fixture.json and
 *  sleep.json), to compare page by page with the PC's report. Skipped without the fixture. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReportCompareTest {
    @Test fun pages() {
        val dir = System.getenv("REPORT_FIXTURE")
        assumeTrue(dir != null && File(dir, "fixture.json").exists())
        val store = AppStore(null)
        store.absorb(SyncState.from(org.json.JSONObject(File(dir, "fixture.json").readText())))
        val sj = org.json.JSONObject(File(dir, "sleep.json").readText())
        val sleep = sj.keys().asSequence().associateWith { sj.getDouble(it) }
        val bitmaps = mutableListOf<android.graphics.Bitmap>()
        Report(store, "2026-10-27", "2026-11-25", includeNotes = true, sleep = sleep,
            includeDreams = true, dreamText = true, dose = store.doseFeed).draw(object : Report.Pages {
            override fun start(number: Int): android.graphics.Canvas {
                val b = android.graphics.Bitmap.createBitmap(1190, 1684, android.graphics.Bitmap.Config.ARGB_8888)
                bitmaps.add(b)
                return android.graphics.Canvas(b).apply { scale(2f, 2f) }
            }
            override fun end() {}
        })
        bitmaps.forEachIndexed { i, b ->
            java.io.FileOutputStream(File(dir, "android-${i + 1}.png")).use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
