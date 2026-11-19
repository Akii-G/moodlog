package io.github.akiig.moodlog

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Finds a computer's address from its ".local" name (like pop-os.local), for phones that
 * can't do it themselves. Asks once on the local network (multicast DNS, the way your PC
 * announces its name) and reads the first answer. Nothing leaves your Wi-Fi.
 */
object Mdns {
    private val group: InetAddress = InetAddress.getByName("224.0.0.251")

    fun query(name: String, id: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in name.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.write(byteArrayOf(0, 1, 0x80.toByte(), 1))   // type A, class IN, unicast reply wanted
        return out.toByteArray()
    }

    private fun skipName(b: ByteArray, start: Int): Int {
        var i = start
        while (i < b.size) {
            val len = b[i].toInt() and 0xff
            if (len == 0) return i + 1
            if (len and 0xc0 == 0xc0) return i + 2
            i += len + 1
        }
        throw IllegalArgumentException("bad name")
    }

    /** The first IPv4 address in a DNS answer, or null. */
    fun parse(b: ByteArray, length: Int = b.size): InetAddress? {
        if (length < 12) return null
        fun u16(i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
        val questions = u16(4)
        val answers = u16(6) + u16(8) + u16(10)
        var i = 12
        repeat(questions) { i = skipName(b, i) + 4 }
        repeat(answers) {
            i = skipName(b, i)
            if (i + 10 > length) return null
            val type = u16(i)
            val size = u16(i + 8)
            i += 10
            if (type == 1 && size == 4 && i + 4 <= length) {
                return InetAddress.getByAddress(b.copyOfRange(i, i + 4))
            }
            i += size
        }
        return null
    }

    fun resolve(name: String, timeoutMs: Int = 1500, tries: Int = 2): InetAddress? {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val q = query(name)
            repeat(tries) {
                socket.send(DatagramPacket(q, q.size, group, 5353))
                val buf = ByteArray(1500)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(p)
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                    try {
                        parse(buf, p.length)?.let { return it }
                    } catch (e: Exception) {
                        // not an answer we can read: wait for the next one
                    }
                }
            }
        }
        return null
    }
}
