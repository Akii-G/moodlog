package io.github.akiig.moodlog

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One phone, two PCs (started by test/test_two_pcs.py; skipped otherwise). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoPCsTest {
    @Test fun everythingReachesBothPCs() = runBlocking {
        val list = System.getenv("MOODLOG_TEST_PCS")
        assumeTrue(list != null)
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = Prefs(app)
        val a = JSONArray(list)
        prefs.pcs = (0 until a.length()).map { a.getJSONObject(it) }.map {
            PCLink(name = it.getString("name"), server = it.getString("server"), token = it.getString("token"))
        }
        val store = AppStore(null)
        store.addEntry("2026-11-18", "08:15", 4, 3, 2, "from the phone")
        val client = SyncClient({ "" }, { "" }, "Test Android", Dispatchers.Unconfined)
        val sync = SyncCoordinator(prefs, client, HealthReader(app), MainScope())
        sync.run(store)
        assertTrue(sync.status, sync.status.startsWith("Synced at"))
        assertTrue(store.entries.any { it.note == "from linux" } && store.entries.any { it.note == "from mac" })
        for (pc in prefs.pcs) assertTrue(sync.pcStatus[pc.id] ?: "", (sync.pcStatus[pc.id] ?: "").startsWith("Synced at"))

        // a PC that is off is skipped, and named
        prefs.pcs = prefs.pcs + PCLink(name = "Off", server = "http://127.0.0.1:9", token = "x")
        sync.run(store)
        assertTrue(sync.status, sync.status.contains("Off: not reached"))
        assertEquals(3, prefs.pcs.size)
    }
}
