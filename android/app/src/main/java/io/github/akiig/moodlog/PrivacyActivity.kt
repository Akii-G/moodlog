package io.github.akiig.moodlog

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.akiig.moodlog.ui.MoodlogTheme
import io.github.akiig.moodlog.ui.PrivacyScreen

/** Opened by Health Connect when it shows why Moodlog asks for health data. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTranslations(this)
        enableEdgeToEdge()
        setContent {
            MoodlogTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) { PrivacyScreen(onBack = { finish() }) }
            }
        }
    }
}

object BuildConfigVersion {
    fun name(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
}
