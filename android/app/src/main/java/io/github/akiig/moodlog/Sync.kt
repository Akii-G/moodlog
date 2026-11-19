package io.github.akiig.moodlog

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

class SyncException(message: String) : Exception(message)

/** Address, token and switches, kept in the app's private settings. */
class Prefs(context: Context) {
    val p: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var server: String
        get() = p.getString("server", "") ?: ""
        set(v) = p.edit().putString("server", v).apply()
    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) = p.edit().putString("token", v).apply()
    var autoSync: Boolean
        get() = p.getBoolean("autoSync", true)
        set(v) = p.edit().putBoolean("autoSync", v).apply()
    var sendHealth: Boolean
        get() = p.getBoolean("sendHealth", false)
        set(v) = p.edit().putBoolean("sendHealth", v).apply()
    var tourDone: Boolean
        get() = p.getBoolean("tourDone", false)
        set(v) = p.edit().putBoolean("tourDone", v).apply()
    var lastHealth: Long
        get() = p.getLong("lastHealth", 0)
        set(v) = p.edit().putLong("lastHealth", v).apply()

    val isConfigured get() = server.isNotBlank() && token.isNotBlank()
}

class SyncClient(
    private val server: () -> String,
    private val tokenSource: () -> String,
    private val deviceName: String,
    private val main: CoroutineDispatcher = Dispatchers.Main,
) {
    constructor(prefs: Prefs, deviceName: String) : this({ prefs.server }, { prefs.token }, deviceName)

    private val token get() = tokenSource().trim()

    /** The address from Settings. Accepts "pop-os.local:8765", a full URL, or the
     *  shortcut's address ending in /health. */
    fun baseUrl(): URI {
        var raw = server().trim()
        if (raw.isEmpty() || token.isEmpty()) throw SyncException(tr("Add your PC's address and token in Settings first."))
        if (!raw.lowercase().startsWith("http://") && !raw.lowercase().startsWith("https://")) raw = "http://$raw"
        raw = raw.trimEnd('/')
        if (raw.lowercase().endsWith("/health")) raw = raw.dropLast("/health".length)
        val uri = try { URI(raw) } catch (e: Exception) { null }
        if (uri?.host.isNullOrEmpty()) throw SyncException(tr("The address doesn't look right. It should be like http://pop-os.local:8765"))
        return uri!!
    }

    private val resolved = HashMap<String, InetAddress>()

    /** pop-os.local -> its address, asking the network ourselves if the phone can't. */
    private fun hostFor(uri: URI): String {
        val host = uri.host
        if (!host.lowercase().endsWith(".local")) return host
        try {
            InetAddress.getByName(host)
            return host
        } catch (e: UnknownHostException) {
            // many Android phones can't look up .local names: do it here
        }
        val ip = resolved[host] ?: Mdns.resolve(host)?.also { resolved[host] = it }
        return ip?.hostAddress ?: host
    }

    /** Sends `payload` encrypted to `path` (e.g. "/api/e/sync") and returns the decrypted reply.
     *  The token is only used as the key: it is never sent. */
    fun request(path: String, payload: ByteArray): ByteArray {
        val base = baseUrl()
        val host = hostFor(base)
        val port = if (base.port > 0) ":${base.port}" else ""
        val url = URL("${base.scheme}://$host$port${base.rawPath ?: ""}$path")
        val conn = url.openConnection() as HttpURLConnection
        val code: Int
        val body: String
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val sealed = SyncCrypto.seal(payload, token, path).toByteArray()
            conn.setFixedLengthStreamingMode(sealed.size)
            conn.outputStream.use { it.write(sealed) }
            code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: IOException) {
            throw SyncException(tr("Can't reach your PC. Is it on, with Phone sync switched on, and is your phone on the same Wi-Fi? (%@)",
                e.localizedMessage ?: e.javaClass.simpleName))
        } finally {
            conn.disconnect()
        }
        if (code == 401) throw SyncException(tr("Wrong token. Copy it again from Moodlog on your PC (Phone sync)."))
        if (code == 404) throw SyncException(tr("Your PC runs an older Moodlog. Update it to 2.5 or newer: syncing is now encrypted."))
        if (code !in 200..299) {
            val message = try { JSONObject(body).optString("error") } catch (e: Exception) { "" }
            throw SyncException(message.ifEmpty { tr("Your PC answered with error %@.", code) })
        }
        return try {
            SyncCrypto.open(body, token, path)
        } catch (e: SyncCrypto.Unreadable) {
            throw SyncException(tr("Wrong token. Copy it again from Moodlog on your PC (Phone sync)."))
        }
    }

    private val unexpected get() = tr("Your PC sent something unexpected. Update Moodlog on your PC to 2.5 or newer.")

    /** Checks the address and token. Returns e.g. "Connected to Moodlog 2.5.0 on your PC". */
    fun ping(): String {
        val data = request("/api/e/ping", "{}".toByteArray())
        val version = try { JSONObject(String(data)).getString("version") } catch (e: Exception) {
            throw SyncException(unexpected)
        }
        return tr("Connected to Moodlog %@ on your PC (encrypted)", version)
    }

    /** Sends everything on the phone, gets the merged state back and merges it here. */
    suspend fun sync(store: AppStore): Int {
        val body = withContext(main) { store.toJson() }
            .put("device", deviceName).put("app", "android").toString().toByteArray()
        val data = withContext(Dispatchers.IO) { request("/api/e/sync", body) }
        val remote = try { SyncState.from(JSONObject(String(data))) } catch (e: Exception) {
            throw SyncException(unexpected)
        }
        return withContext(main) { store.absorb(remote) }
    }

    /** Sends health lines (the same format as the iPhone app and the shortcut), encrypted. */
    fun sendHealth(lines: List<String>): String {
        val body = JSONObject().put("lines", lines.joinToString("\n")).toString().toByteArray()
        val data = request("/api/e/health", body)
        val message = try { JSONObject(String(data)).optString("message") } catch (e: Exception) { "" }
        return message.ifEmpty { tr("Sent.") }.trim()
    }

    companion object {
        fun deviceName(context: Context): String =
            Settings.Global.getString(context.contentResolver, "device_name")
                ?: "${Build.MANUFACTURER} ${Build.MODEL}"
    }
}

/** Runs syncs: when the app opens, a couple of seconds after a change, and on request. */
class SyncCoordinator(
    private val prefs: Prefs,
    private val client: SyncClient,
    private val health: HealthReader,
    private val scope: CoroutineScope,
) {
    private var lastStatus by mutableStateOf("")
    /** What the last sync did, or "Not synced yet" (in the current language). */
    var status: String
        get() = lastStatus.ifEmpty { tr("Not synced yet") }
        set(v) { lastStatus = v }
    var busy by mutableStateOf(false)
    var healthStatus by mutableStateOf("")

    private var pending: Job? = null

    fun syncSoon(store: AppStore, delayMs: Long) {
        if (!prefs.autoSync || !prefs.isConfigured) return
        pending?.cancel()
        pending = scope.launch {
            if (delayMs > 0) delay(delayMs)
            run(store)
        }
    }

    suspend fun run(store: AppStore) {
        if (busy) return
        busy = true
        try {
            val changes = client.sync(store)
            val time = Day.now()
            status = if (changes > 0) trn("Synced at %2\$@ · %1\$@ change", "Synced at %2\$@ · %1\$@ changes", changes, time)
                     else tr("Synced at %@", time)
            if (prefs.sendHealth && System.currentTimeMillis() - prefs.lastHealth > 3_600_000) {
                sendHealth()
            }
        } catch (e: SyncException) {
            status = e.message ?: ""
        } catch (e: Exception) {
            status = e.localizedMessage ?: e.javaClass.simpleName
        } finally {
            busy = false
        }
    }

    suspend fun sendHealth() {
        if (!health.isAvailable()) {
            healthStatus = tr("Health Connect isn't available on this phone. Install it from the Play Store, then try again.")
            return
        }
        if (!health.hasPermissions()) {
            healthStatus = tr("Allow Moodlog to read steps, resting heart rate and sleep first (the button above).")
            return
        }
        try {
            val lines = health.lines(7)
            if (lines.isEmpty()) {
                healthStatus = tr("No health data found for the last 7 days. Check that your watch or fitness app shares it with Health Connect.")
                return
            }
            val answer = withContext(Dispatchers.IO) { client.sendHealth(lines) }
            prefs.lastHealth = System.currentTimeMillis()
            healthStatus = answer
        } catch (e: SyncException) {
            healthStatus = e.message ?: ""
        } catch (e: Exception) {
            healthStatus = e.localizedMessage ?: e.javaClass.simpleName
        }
    }
}
