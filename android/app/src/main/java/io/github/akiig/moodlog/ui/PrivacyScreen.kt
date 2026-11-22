package io.github.akiig.moodlog.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.appLocale
import io.github.akiig.moodlog.tr

fun privacyUrl(): String = if (appLocale().language == "it")
    "https://github.com/Akii-G/sottovoce/blob/main/PRIVACY.it.md"
else "https://github.com/Akii-G/sottovoce/blob/main/PRIVACY.md"

/** The privacy policy in short (the same points as PRIVACY.md), with a link to the full one. */
@Composable
fun PrivacyScreen(back: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val points = listOf(
        tr("Nobody collects your data") to tr("Sottovoce has no account, no server, no analytics and no ads. The developer never sees anything you write."),
        tr("It stays on your devices") to tr("Everything is saved inside this app, on your own PC if you sync, and in the backup file if you pick one."),
        tr("Sync is encrypted") to tr("Your entries go straight to your PC over your own Wi-Fi, encrypted with your token. The token itself is never sent."),
        tr("Health data only if you choose") to tr("Steps, resting heart rate and sleep are read from Health Connect only if you allow it and turn it on in Settings, and only to show them next to your mood on your PC. Sottovoce never writes to Health Connect."),
        tr("No internet") to tr("Sottovoce never connects to the internet. Only the link below opens a page in your browser."),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackTitle(back, tr("Privacy"), onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Section(tr("Your data is yours")) {
                points.forEachIndexed { i, (title, text) ->
                    if (i > 0) Line()
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Section {
                LinkRow(tr("Read the full policy")) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl())))
                }
            }
            Spacer(Modifier.height(TabBarSpace))
        }
    }
}
