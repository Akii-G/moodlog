package io.github.akiig.moodlog

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
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

    // the daily reminder
    var reminderOn: Boolean
        get() = p.getBoolean("reminderOn", false)
        set(v) = p.edit().putBoolean("reminderOn", v).apply()
    var reminderTime: String
        get() = p.getString("reminderTime", "20:30") ?: "20:30"
        set(v) = p.edit().putString("reminderTime", v).apply()

    // backup to a file the person picked (Google Drive, the phone, a memory card)
    var backupUri: String
        get() = p.getString("backupUri", "") ?: ""
        set(v) = p.edit().putString("backupUri", v).apply()
    var backupName: String
        get() = p.getString("backupName", "") ?: ""
        set(v) = p.edit().putString("backupName", v).apply()
    var lastBackup: String
        get() = p.getString("lastBackup", "") ?: ""
        set(v) = p.edit().putString("lastBackup", v).apply()

    // the kind word on hard stretches
    var kindWord: Boolean
        get() = p.getBoolean("kindWord", true)
        set(v) = p.edit().putBoolean("kindWord", v).apply()
    var kindDismissed: String
        get() = p.getString("kindDismissed", "") ?: ""
        set(v) = p.edit().putString("kindDismissed", v).apply()

    // "Your week, gently"
    var weekCard: Boolean
        get() = p.getBoolean("weekCard", true)
        set(v) = p.edit().putBoolean("weekCard", v).apply()
    var weekHidden: String
        get() = p.getString("weekHidden", "") ?: ""
        set(v) = p.edit().putString("weekHidden", v).apply()

    // Customize your experience
    var showDreams: Boolean
        get() = p.getBoolean("showDreams", true)
        set(v) = p.edit().putBoolean("showDreams", v).apply()
    /** Days with several moods: "grid" shows them all, "latest" only the last one. */
    var several: String
        get() = p.getString("severalMoods", "grid") ?: "grid"
        set(v) = p.edit().putString("severalMoods", v).apply()
    var privateTick: Boolean
        get() = p.getBoolean("privateTick", true)
        set(v) = p.edit().putBoolean("privateTick", v).apply()
    var privateDefault: Boolean
        get() = p.getBoolean("privateDefault", false)
        set(v) = p.edit().putBoolean("privateDefault", v).apply()
    /** Feeling words next to a mood, and the writing prompt in an empty note. */
    var feelings: Boolean
        get() = p.getBoolean("showFeelings", true)
        set(v) = p.edit().putBoolean("showFeelings", v).apply()
    var prompts: Boolean
        get() = p.getBoolean("showPrompts", true)
        set(v) = p.edit().putBoolean("showPrompts", v).apply()
    /** PHQ-9 and GAD-7 in Settings, the report and the two-week reminder. */
    var questionnaires: Boolean
        get() = p.getBoolean("showQuestionnaires", true)
        set(v) = p.edit().putBoolean("showQuestionnaires", v).apply()
    var surveyRemind: Boolean
        get() = p.getBoolean("surveyRemind", true)
        set(v) = p.edit().putBoolean("surveyRemind", v).apply()
    var customizeSeen: Boolean
        get() = p.getBoolean("customizeSeen", false)
        set(v) = p.edit().putBoolean("customizeSeen", v).apply()

    /** The version whose "What's new" was last shown. */
    var lastSeenVersion: String
        get() = p.getString("lastSeenVersion", "") ?: ""
        set(v) = p.edit().putString("lastSeenVersion", v).apply()

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

    /** Where pop-os.local was last found, so the next sync is quick. */
    var remember: ((host: String, ip: String) -> Unit)? = null
    var recall: ((host: String) -> String?)? = null
    /** Lets the phone receive network-wide (multicast) answers while looking a name up. */
    var multicastLock: (() -> (() -> Unit))? = null

    /** The addresses to try, in order. For pop-os.local: where it was last found, the name
     *  itself, "pop-os" on the router's own DNS, then asking the network ourselves (many
     *  Android phones can't look up .local names). Each later one is only tried if the
     *  earlier ones fail. */
    private fun candidates(host: String): Sequence<String> = sequence {
        if (!host.lowercase().endsWith(".local")) {
            yield(host); return@sequence
        }
        val tried = mutableSetOf<String>()
        recall?.invoke(host)?.let { if (tried.add(it)) yield(it) }
        val system = try { InetAddress.getByName(host).hostAddress } catch (e: Exception) { null }
        if (system != null && tried.add(system)) yield(system)
        val short = host.dropLast(".local".length)
        val router = try { InetAddress.getByName(short).takeIf { !it.isLoopbackAddress }?.hostAddress } catch (e: Exception) { null }
        if (router != null && tried.add(router)) yield(router)
        val release = try { multicastLock?.invoke() } catch (e: Exception) { null }
        val asked = try { Mdns.resolve(host)?.hostAddress } finally { release?.invoke() }
        if (asked != null && tried.add(asked)) yield(asked)
        if (tried.isEmpty()) throw SyncException(tr("Can't find %@ on your Wi-Fi. Is the PC on and on the same Wi-Fi? If it still fails, use the other address Phone sync shows on the PC (If that fails).", host))
    }

    private fun send(url: URL, sealed: ByteArray, timeout: Int): Pair<Int, String> {
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeout
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(sealed.size)
            conn.outputStream.use { it.write(sealed) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    /** Sends `payload` encrypted to `path` (e.g. "/api/e/sync") and returns the decrypted reply.
     *  The token is only used as the key: it is never sent. */
    fun request(path: String, payload: ByteArray): ByteArray {
        val base = baseUrl()
        val port = if (base.port > 0) ":${base.port}" else ""
        val sealed = SyncCrypto.seal(payload, token, path).toByteArray()
        var result: Pair<Int, String>? = null
        var failure: IOException? = null
        for (host in candidates(base.host)) {
            val shown = if (host.contains(':')) "[$host]" else host
            try {
                result = send(URL("${base.scheme}://$shown$port${base.rawPath ?: ""}$path"), sealed, 5_000)
                if (host != base.host) remember?.invoke(base.host, host)
                break
            } catch (e: IOException) {
                failure = e   // try the next address
            }
        }
        val (code, body) = result ?: throw SyncException(tr("Can't reach your PC. Is it on, with Phone sync switched on, and is your phone on the same Wi-Fi? (%@)",
            failure?.localizedMessage ?: failure?.javaClass?.simpleName ?: ""))
        if (code == 401) throw SyncException(tr("Wrong token. Copy it again from Sottovoce on your PC (Phone sync)."))
        if (code == 404) throw SyncException(tr("Your PC runs an older Sottovoce. Update it to 2.5 or newer: syncing is now encrypted."))
        if (code !in 200..299) {
            val message = try { JSONObject(body).optString("error") } catch (e: Exception) { "" }
            throw SyncException(message.ifEmpty { tr("Your PC answered with error %@.", code) })
        }
        return try {
            SyncCrypto.open(body, token, path)
        } catch (e: SyncCrypto.Unreadable) {
            throw SyncException(tr("Wrong token. Copy it again from Sottovoce on your PC (Phone sync)."))
        }
    }

    private val unexpected get() = tr("Your PC sent something unexpected. Update Sottovoce on your PC to 2.5 or newer.")

    /** Checks the address and token. Returns e.g. "Connected to Sottovoce 2.5.0 on your PC". */
    fun ping(): String {
        val data = request("/api/e/ping", "{}".toByteArray())
        val version = try { JSONObject(String(data)).getString("version") } catch (e: Exception) {
            throw SyncException(unexpected)
        }
        return tr("Connected to Sottovoce %@ on your PC (encrypted)", version)
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
    private var again = false

    fun syncSoon(store: AppStore, delayMs: Long) {
        if (!prefs.autoSync || !prefs.isConfigured) return
        pending?.cancel()
        pending = scope.launch {
            if (delayMs > 0) delay(delayMs)
            // The sync gets a job of its own: a newer syncSoon only cancels the wait,
            // never a sync that is already talking to the PC.
            scope.launch { run(store) }
        }
    }

    suspend fun run(store: AppStore) {
        if (busy) {         // one at a time; sync once more when this one ends
            again = true
            return
        }
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
        } catch (e: CancellationException) {
            throw e         // stopped on purpose: not an error to show
        } catch (e: Exception) {
            status = e.localizedMessage ?: e.javaClass.simpleName
        } finally {
            busy = false
        }
        if (again) {
            again = false
            run(store)
        }
    }

    suspend fun sendHealth() {
        if (!health.isAvailable()) {
            healthStatus = tr("Health Connect isn't available on this phone. Install it from the Play Store, then try again.")
            return
        }
        if (!health.hasPermissions()) {
            healthStatus = tr("Allow Sottovoce to read steps, resting heart rate and sleep first (the button above).")
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
