package io.github.akiig.moodlog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Syncs with a real Moodlog receiver. Run by test/test_android_sync.py, which starts the
 *  PC's receiver with test data and checks the PC's files afterwards. Skipped otherwise. */
class LiveSyncTest {
    private val server = System.getenv("MOODLOG_TEST_SERVER") ?: ""
    private val token = System.getenv("MOODLOG_TEST_TOKEN") ?: ""

    @Test fun syncWithThePc() = runBlocking {
        assumeTrue(server.isNotEmpty())
        val client = SyncClient({ server }, { token }, "Test Android", Dispatchers.Unconfined)
        assertTrue(client.ping(), client.ping().startsWith("Connected to Moodlog"))

        val store = AppStore(null)
        store.addEntry("2026-11-18", "08:15", 4, 3, 2, "from android è")
        store.addEvent("2026-11-17", "medication", "New dose", "")
        store.addDream("2026-11-18", "pleasant", listOf("vivid"), "a dream from android")
        val gone = store.entries.first()
        store.addEntry("2026-11-18", "09:00", 1, 0, 0, "to delete")
        val changes = client.sync(store)
        assertTrue("the PC's seeded entry arrives", store.entries.any { it.note == "from the pc" })
        assertTrue(changes >= 1)

        store.delete(store.entries.first { it.note == "to delete" })
        client.sync(store)
        assertTrue(store.entries.none { it.note == "to delete" })
        assertTrue(store.entries.any { it.id == gone.id })

        val answer = client.sendHealth(listOf("steps;2026-11-18;8234", "resting_hr;2026-11-18;61",
            "sleep;2026-11-17T23:10:00Z;2026-11-18T03:10:00Z;Core", "sleep;2026-11-18T03:10:00Z;2026-11-18T06:40:00Z;REM"))
        assertTrue(answer, answer.startsWith("OK. Stored"))

        val wrong = SyncClient({ server }, { "wrong-token-123456789" }, "x", Dispatchers.Unconfined)
        try { wrong.ping(); throw AssertionError("wrong token accepted") } catch (e: SyncException) {
            assertTrue(e.message, e.message!!.startsWith("Wrong token"))
        }
        // the shortcut-style address works too
        val shortcut = SyncClient({ server.removePrefix("http://") + "/health" }, { token }, "x", Dispatchers.Unconfined)
        assertEquals(client.ping(), shortcut.ping())
    }
}
