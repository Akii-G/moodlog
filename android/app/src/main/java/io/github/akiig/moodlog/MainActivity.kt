package io.github.akiig.moodlog

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.akiig.moodlog.ui.MoodlogApp
import io.github.akiig.moodlog.ui.MoodlogTheme
import io.github.akiig.moodlog.ui.TourState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File

/** Everything the screens share. One per process, so a screen rotation keeps it. */
class AppState private constructor(app: Application) {
    val prefs = Prefs(app)
    val store = AppStore(File(app.filesDir, "moodlog.json"))
    val health = HealthReader(app)
    val scope: CoroutineScope = MainScope()
    val client = SyncClient(prefs, SyncClient.deviceName(app))
    val sync = SyncCoordinator(prefs, client, health, scope)
    val tour = TourState()
    private val context = app.applicationContext

    /** Set when the reminder notification was tapped: open today's entry. */
    var openToday by androidx.compose.runtime.mutableStateOf(false)
    var backupStatus by androidx.compose.runtime.mutableStateOf("")
    private var pendingBackup: kotlinx.coroutines.Job? = null

    /** Writes the backup file, if one was picked, a few seconds after the last change. */
    fun backupSoon(delayMs: Long = 4_000) {
        if (prefs.backupUri.isEmpty()) return
        pendingBackup?.cancel()
        pendingBackup = scope.launch {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            backupNow()
        }
    }

    suspend fun backupNow(): Boolean {
        val uri = android.net.Uri.parse(prefs.backupUri.ifEmpty { return false })
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Backup.write(context, uri, store) }
        if (ok) {
            prefs.lastBackup = Day.today + " " + Day.now()
            backupStatus = ""
        } else {
            backupStatus = tr("Couldn't write the backup file. Pick it again below, or check that the Drive app is signed in.")
        }
        return ok
    }

    init {
        store.onChange = {
            sync.syncSoon(store, 2_000)
            backupSoon()
            Reminder.schedule(context)     // logging today means no reminder today
        }
        client.remember = { host, ip -> prefs.p.edit().putString("ip:$host", ip).apply() }
        client.recall = { host -> prefs.p.getString("ip:$host", null) }
        client.multicastLock = {
            val wifi = app.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val lock = wifi.createMulticastLock("moodlog-find-pc").apply { setReferenceCounted(false); acquire() }
            ({ lock.release() })
        }
    }

    companion object {
        @Volatile private var instance: AppState? = null
        fun get(app: Application): AppState =
            instance ?: synchronized(this) { instance ?: AppState(app).also { instance = it } }
    }
}

val LocalApp = staticCompositionLocalOf<AppState> { error("no AppState") }

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTranslations(this)
        enableEdgeToEdge()
        state = AppState.get(application)
        if (intent?.getBooleanExtra(Reminder.OPEN_TODAY, false) == true) state.openToday = true
        Reminder.createChannel(this)
        Reminder.schedule(this)
        setContent {
            MoodlogTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalApp provides state) {
                    MoodlogApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Reminder.OPEN_TODAY, false)) state.openToday = true
    }

    override fun onPause() {
        super.onPause()
        state.backupSoon(0)
    }

    override fun onResume() {
        super.onResume()
        state.sync.syncSoon(state.store, 0)
    }
}
