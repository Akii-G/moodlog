package io.github.akiig.moodlog

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

/** The same fixed values test/test_encrypted_sync.py (PC) and ios/CryptoCheck (iPhone) check,
 *  so all three apps are sure to encrypt the same way. */
class SyncCryptoTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test fun keyMatchesThePc() {
        assertEquals("3f50a3ff4c7099fe930d06eb96376ad1f877327018cb8011e603d99f54741887",
            hex(SyncCrypto.key("test-token-0123456789")))
    }

    @Test fun ciphertextMatchesThePc() {
        val doc = JSONObject(SyncCrypto.sealWith("{\"a\":1}".toByteArray(), "test-token-0123456789",
            "/api/e/ping", ByteArray(12), reply = false))
        assertEquals("1c34af008a5dc72ec3fd26e58f41e66bbf3296134970b2", hex(Base64.getDecoder().decode(doc.getString("c"))))
        assertEquals(1, doc.getInt("v"))
    }

    @Test fun replyRoundTrip() {
        val sealed = SyncCrypto.sealWith("{\"ok\":true}".toByteArray(), "tok-aaaaaaaaaaaaaaaa", "/api/e/sync",
            ByteArray(12) { it.toByte() }, reply = true)
        assertArrayEquals("{\"ok\":true}".toByteArray(), SyncCrypto.open(sealed, "tok-aaaaaaaaaaaaaaaa", "/api/e/sync"))
    }

    @Test fun refusesWrongTokenPathDirectionOrTampering() {
        val sealed = SyncCrypto.sealWith("{}".toByteArray(), "tok-aaaaaaaaaaaaaaaa", "/api/e/sync", ByteArray(12), true)
        val tampered = JSONObject(sealed).let {
            val c = Base64.getDecoder().decode(it.getString("c")); c[0] = (c[0].toInt() xor 1).toByte()
            it.put("c", Base64.getEncoder().encodeToString(c)).toString()
        }
        for ((body, token, path, reply) in listOf(
            Quad(sealed, "tok-bbbbbbbbbbbbbbbb", "/api/e/sync", true),
            Quad(sealed, "tok-aaaaaaaaaaaaaaaa", "/api/e/ping", true),
            Quad(sealed, "tok-aaaaaaaaaaaaaaaa", "/api/e/sync", false),
            Quad(tampered, "tok-aaaaaaaaaaaaaaaa", "/api/e/sync", true),
            Quad("not json", "tok-aaaaaaaaaaaaaaaa", "/api/e/sync", true))) {
            try {
                SyncCrypto.open(body, token, path, reply); fail("opened: $token $path $reply")
            } catch (e: SyncCrypto.Unreadable) { }
        }
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: Boolean)
}
