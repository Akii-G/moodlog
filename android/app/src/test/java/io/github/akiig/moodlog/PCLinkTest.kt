package io.github.akiig.moodlog

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PCLinkTest {
    @Test
    fun titleComesFromTheNameOrTheAddress() {
        assertEquals("Mac", PCLink(name = " Mac ", server = "http://pop-os.local:8765").title)
        assertEquals("pop-os", PCLink(server = "http://pop-os.local:8765").title)
        assertEquals("pop-os", PCLink(server = "pop-os.local:8765/health").title)
        assertEquals("192.168.1.20", PCLink(server = "192.168.1.20:8765").title)
        assertEquals("New PC", PCLink().title)
    }

    @Test
    fun configuredNeedsAddressAndToken() {
        assertFalse(PCLink(server = "pop-os.local:8765").isConfigured)
        assertTrue(PCLink(server = "pop-os.local:8765", token = "abc").isConfigured)
    }

    @Test
    fun roundTrip() {
        val pc = PCLink(name = "Mac", server = "http://mac.local:8765", token = "t", lastHealth = 42)
        assertEquals(pc, PCLink.from(JSONObject(pc.toJson().toString())))
    }
}
