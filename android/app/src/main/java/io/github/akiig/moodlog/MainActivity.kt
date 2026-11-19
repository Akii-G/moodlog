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

    init {
        store.onChange = { sync.syncSoon(store, 2_000) }
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
        setContent {
            MoodlogTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalApp provides state) {
                    MoodlogApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        state.sync.syncSoon(state.store, 0)
    }
}
