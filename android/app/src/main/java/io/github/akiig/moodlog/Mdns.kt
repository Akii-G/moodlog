package io.github.akiig.moodlog

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Finds a computer's address from its ".local" name (like pop-os.local), for phones that
 * can't do it themselves. Asks on the local network (multicast DNS, the way your PC
 * announces its name) and reads the first answer. Nothing leaves your Wi-Fi.
 *
 * Asks two ways at once, since phones and routers differ: a question that wants a direct
 * reply (from a temporary port), and an ordinary one whose answer everyone on the network
 * hears (on port 5353).
 */
object Mdns {
    private val group: InetAddress = InetAddress.getByName("224.0.0.251")

    fun query(name: String, unicastReply: Boolean = true, id: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in name.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.write(byteArrayOf(0, 1, if (unicastReply) 0x80.toByte() else 0, 1))   // type A, class IN
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

    private fun readName(b: ByteArray, start: Int, length: Int): String {
        val parts = mutableListOf<String>()
        var i = start
        var jumps = 0
        while (i < length && jumps < 20) {
            val len = b[i].toInt() and 0xff
            if (len == 0) break
            if (len and 0xc0 == 0xc0) {
                i = ((len and 0x3f) shl 8) or (b[i + 1].toInt() and 0xff); jumps++; continue
            }
            parts.add(String(b, i + 1, len, Charsets.UTF_8))
            i += len + 1
        }
        return parts.joinToString(".")
    }

    /** The first IPv4 address for `name` (any name if null) in a DNS answer, or null. */
    fun parse(b: ByteArray, length: Int = b.size, name: String? = null): InetAddress? {
        if (length < 12) return null
        fun u16(i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
        if (u16(2) and 0x8000 == 0) return null              // a question, not an answer
        val questions = u16(4)
        val answers = u16(6) + u16(8) + u16(10)
        var i = 12
        repeat(questions) { i = skipName(b, i) + 4 }
        repeat(answers) {
            val owner = readName(b, i, length)
            i = skipName(b, i)
            if (i + 10 > length) return null
            val type = u16(i)
            val size = u16(i + 8)
            i += 10
            if (type == 1 && size == 4 && i + 4 <= length &&
                (name == null || owner.equals(name.trimEnd('.'), ignoreCase = true))) {
                return InetAddress.getByAddress(b.copyOfRange(i, i + 4))
            }
            i += size
        }
        return null
    }

    /** The Wi-Fi (or Ethernet) interface with an IPv4 address, to ask on the right network. */
    fun localInterface(): NetworkInterface? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.supportsMulticast() &&
                      it.inetAddresses.toList().any { a -> a is Inet4Address } }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else if (it.name.startsWith("eth")) 1 else 2 }
            .firstOrNull()
    } catch (e: Exception) {
        null
    }

    fun resolve(name: String, timeoutMs: Int = 3000, iface: NetworkInterface? = localInterface(),
                port: Int = 5353, target: InetAddress = group): InetAddress? {
        val found = AtomicReference<InetAddress?>(null)
        val deadline = System.currentTimeMillis() + timeoutMs

        fun listen(socket: MulticastSocket) = thread(isDaemon = true) {
            val buf = ByteArray(9000)
            while (found.get() == null && System.currentTimeMillis() < deadline) {
                val p = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(p)
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    break
                }
                try {
                    parse(buf, p.length, name)?.let { found.compareAndSet(null, it) }
                } catch (e: Exception) {
                    // not an answer we can read
                }
            }
        }

        // 1. direct reply to a temporary port
        val direct = MulticastSocket(0).apply {
            soTimeout = 250
            timeToLive = 255
            iface?.let { try { networkInterface = it } catch (e: Exception) { } }
        }
        // 2. the ordinary answer everyone hears, on port 5353 (may be taken: then skip it)
        val shared = try {
            MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
                soTimeout = 250
                if (iface != null) joinGroup(InetSocketAddress(group, port), iface) else @Suppress("DEPRECATION") joinGroup(group)
            }
        } catch (e: Exception) {
            null
        }
        try {
            val listeners = listOfNotNull(listen(direct), shared?.let { listen(it) })
            var tries = 0
            while (found.get() == null && System.currentTimeMillis() < deadline) {
                val qu = query(name, unicastReply = true)
                val qm = query(name, unicastReply = false)
                try {
                    direct.send(DatagramPacket(qu, qu.size, target, port))
                    direct.send(DatagramPacket(qm, qm.size, target, port))
                } catch (e: Exception) {
                    // no network: keep waiting for the deadline in case one appears
                }
                tries++
                val wait = minOf(1000L, deadline - System.currentTimeMillis())
                var waited = 0L
                while (found.get() == null && waited < wait) { Thread.sleep(50); waited += 50 }
                if (tries >= 3) break
            }
            listeners.forEach { it.join(300) }
        } finally {
            direct.close()
            shared?.close()
        }
        return found.get()
    }
}
