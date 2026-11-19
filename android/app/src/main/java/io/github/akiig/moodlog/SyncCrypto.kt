package io.github.akiig.moodlog

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted sync with Moodlog on the PC (2.5 and newer).
 *
 * AES-256-GCM with a key derived from the shared token (HKDF-SHA256), so the token itself
 * never crosses the network. A message is JSON {"v": 1, "n": base64 nonce, "c": base64
 * ciphertext+tag}; the additional data binds it to its endpoint and direction. This must
 * match seal()/unseal() in src/moodlog; SyncCryptoTest checks the same fixed values.
 */
object SyncCrypto {
    class Unreadable : Exception("unreadable")

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** HKDF-SHA256(token, salt "moodlog-sync", info "moodlog sync v1"), 32 bytes. */
    fun key(token: String): ByteArray {
        val prk = hmac("moodlog-sync".toByteArray(), token.toByteArray(Charsets.UTF_8))
        // 32 bytes = one block: T(1) = HMAC(PRK, info || 0x01)
        return hmac(prk, "moodlog sync v1".toByteArray() + byteArrayOf(1))
    }

    fun aad(path: String, reply: Boolean): ByteArray =
        "moodlog/v1 ${if (reply) "reply" else "request"} $path".toByteArray(Charsets.UTF_8)

    fun sealWith(plain: ByteArray, token: String, path: String, nonce: ByteArray, reply: Boolean): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(token), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(path, reply))
        val sealed = cipher.doFinal(plain)     // ciphertext followed by the 16-byte tag
        val b64 = Base64.getEncoder()
        return JSONObject().put("v", 1).put("n", b64.encodeToString(nonce))
            .put("c", b64.encodeToString(sealed)).toString()
    }

    /** A request for `path` (e.g. "/api/e/sync"). */
    fun seal(plain: ByteArray, token: String, path: String): String {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return sealWith(plain, token, path, nonce, reply = false)
    }

    /** Opens a message; by default the PC's reply to a request for `path`. */
    fun open(body: String, token: String, path: String, reply: Boolean = true): ByteArray {
        try {
            val doc = JSONObject(body)
            if (doc.optInt("v") != 1) throw Unreadable()
            val n = Base64.getDecoder().decode(doc.getString("n"))
            val c = Base64.getDecoder().decode(doc.getString("c"))
            if (n.size != 12 || c.size < 16) throw Unreadable()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key(token), "AES"), GCMParameterSpec(128, n))
            cipher.updateAAD(aad(path, reply))
            return cipher.doFinal(c)
        } catch (e: Unreadable) {
            throw e
        } catch (e: Exception) {
            throw Unreadable()
        }
    }
}
