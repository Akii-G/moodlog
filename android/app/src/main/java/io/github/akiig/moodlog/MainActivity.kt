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
    /** Set when the questionnaire reminder was tapped: open the questionnaires. */
    var openSurveys by androidx.compose.runtime.mutableStateOf(false)
    /** Set when an appointment reminder was tapped: open "Before the appointment". */
    var openAppointment by androidx.compose.runtime.mutableStateOf(false)
    var backupStatus by androidx.compose.runtime.mutableStateOf("")

    // Customize your experience (state, so the screens follow at once)
    var showDreams by androidx.compose.runtime.mutableStateOf(prefs.showDreams)
        private set
    var several by androidx.compose.runtime.mutableStateOf(prefs.several)
        private set
    fun chooseDreams(on: Boolean) { showDreams = on; prefs.showDreams = on }
    var privateTick by androidx.compose.runtime.mutableStateOf(prefs.privateTick)
        private set
    var privateDefault by androidx.compose.runtime.mutableStateOf(prefs.privateDefault)
        private set
    fun choosePrivateTick(on: Boolean) { privateTick = on; prefs.privateTick = on }
    fun choosePrivateDefault(on: Boolean) { privateDefault = on; prefs.privateDefault = on }
    fun chooseSeveral(mode: String) { several = mode; prefs.several = mode }
    var showFeelings by androidx.compose.runtime.mutableStateOf(prefs.feelings)
        private set
    var showPrompts by androidx.compose.runtime.mutableStateOf(prefs.prompts)
        private set
    fun chooseFeelings(on: Boolean) { showFeelings = on; prefs.feelings = on }
    var showActivities by androidx.compose.runtime.mutableStateOf(prefs.activities)
        private set
    fun chooseActivities(on: Boolean) { showActivities = on; prefs.activities = on }
    fun choosePrompts(on: Boolean) { showPrompts = on; prefs.prompts = on }
    var showQuestionnaires by androidx.compose.runtime.mutableStateOf(prefs.questionnaires)
        private set
    fun chooseQuestionnaires(on: Boolean) {
        showQuestionnaires = on; prefs.questionnaires = on; SurveyReminder.schedule(context)
    }
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
            SurveyReminder.schedule(context)   // a new questionnaire moves the next reminder
            AppointmentReminder.schedule(context)   // a new or moved appointment moves its reminders
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
        if (intent?.getBooleanExtra(SurveyReminder.OPEN_SURVEYS, false) == true) state.openSurveys = true
        if (intent?.getBooleanExtra(AppointmentReminder.OPEN_APPOINTMENT, false) == true) state.openAppointment = true
        Reminder.createChannel(this)
        Reminder.schedule(this)
        SurveyReminder.schedule(this)
        AppointmentReminder.schedule(this)
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
        if (intent.getBooleanExtra(SurveyReminder.OPEN_SURVEYS, false)) state.openSurveys = true
        if (intent.getBooleanExtra(AppointmentReminder.OPEN_APPOINTMENT, false)) state.openAppointment = true
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
