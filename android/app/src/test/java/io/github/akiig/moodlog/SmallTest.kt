package io.github.akiig.moodlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SmallTest {
    @Test fun placeholders() {
        assertEquals("Synced at 09:30 · 3 changes", fill("Synced at %2\$@ · %1\$@ changes", listOf("3", "09:30")))
        assertEquals("a b 100%", fill("%@ %@ 100%%", listOf("a", "b")))
        assertEquals("3 days", trn("%@ day", "%@ days", 3))
        assertEquals("1 day", trn("%@ day", "%@ days", 1))
    }

    @Test fun stringsFileFormat() {
        val m = parseStrings("/* c */\n\"A \\\"q\\\"\" = \"B\\nC\";\n\"x\" = \"y\";")
        assertEquals("B\nC", m["A \"q\""])
        assertEquals("y", m["x"])
    }

    @Test fun mdnsQueryAndAnswer() {
        val q = Mdns.query("pop-os.local")
        // header (12) + 6 "pop-os" + 5 "local" + 0 + type/class
        assertEquals(12 + 7 + 6 + 1 + 4, q.size)
        // an answer: header, the question echoed, one A record with a name pointer
        val answer = byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 1, 0, 1, 0, 0, 0, 0) +
            q.copyOfRange(12, q.size) +
            byteArrayOf(0xc0.toByte(), 12, 0, 1, 0x80.toByte(), 1, 0, 0, 0, 120, 0, 4, 192.toByte(), 168.toByte(), 1, 42)
        assertEquals("192.168.1.42", Mdns.parse(answer)!!.hostAddress)
        assertNull(Mdns.parse(byteArrayOf(0, 0, 0, 0)))
    }

    /** A fake PC answers like avahi does: a direct reply to the asking port, echoing the
     *  question, with the answer for pop-os.local (and an unrelated one first). */
    @Test fun mdnsResolveEndToEnd() {
        val responder = java.net.DatagramSocket(0, java.net.InetAddress.getByName("127.0.0.1"))
        val port = responder.localPort
        kotlin.concurrent.thread(isDaemon = true) {
            val buf = ByteArray(1500)
            repeat(2) {
                val p = java.net.DatagramPacket(buf, buf.size)
                responder.receive(p)
                val q = buf.copyOfRange(0, p.length)
                if (q[2].toInt() and 0x80 != 0) return@repeat
                val question = q.copyOfRange(12, q.size)
                val other = Mdns.query("printer.local").copyOfRange(12, Mdns.query("printer.local").size - 4)
                val answer = byteArrayOf(q[0], q[1], 0x84.toByte(), 0, 0, 1, 0, 2, 0, 0, 0, 0) + question +
                    other + byteArrayOf(0, 1, 0, 1, 0, 0, 0, 120, 0, 4, 10, 0, 0, 9) +
                    byteArrayOf(0xc0.toByte(), 12, 0, 1, 0x80.toByte(), 1, 0, 0, 0, 120, 0, 4, 192.toByte(), 168.toByte(), 1, 42)
                responder.send(java.net.DatagramPacket(answer, answer.size, p.socketAddress))
            }
        }
        val ip = Mdns.resolve("pop-os.local", timeoutMs = 3000, iface = null, port = port,
            target = java.net.InetAddress.getByName("127.0.0.1"))
        responder.close()
        assertEquals("192.168.1.42", ip?.hostAddress)
    }

    @Test fun sleepLines() {
        val a = Instant.parse("2026-11-12T23:40:12.5Z")
        val b = Instant.parse("2026-11-13T00:55:00Z")
        assertEquals(listOf("sleep;2026-11-12T23:40:12Z;2026-11-13T00:55:00Z;Asleep"), HealthReader.sleepLines(a, b, emptyList()))
        assertEquals(listOf("sleep;2026-11-12T23:40:12Z;2026-11-13T00:55:00Z;Deep"),
            HealthReader.sleepLines(a, b, listOf(Triple(a, b, androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_DEEP))))
    }
}
