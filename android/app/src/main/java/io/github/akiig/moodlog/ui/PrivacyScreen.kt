package io.github.akiig.moodlog.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.appLocale
import io.github.akiig.moodlog.tr

fun privacyUrl(): String = if (appLocale().language == "it")
    "https://github.com/Akii-G/moodlog/blob/main/PRIVACY.it.md"
else "https://github.com/Akii-G/moodlog/blob/main/PRIVACY.md"

/** The privacy policy in short (the same points as PRIVACY.md), with a link to the full one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val points = listOf(
        tr("Nobody collects your data") to tr("Moodlog has no account, no server, no analytics and no ads. The developer never sees anything you write."),
        tr("It stays on your devices") to tr("Everything is saved inside this app and, if you sync, on your own PC."),
        tr("Sync is encrypted") to tr("Your entries go straight to your PC over your own Wi-Fi, encrypted with your token. The token itself is never sent."),
        tr("Health data only if you choose") to tr("Steps, resting heart rate and sleep are read from Health Connect only if you allow it and turn it on in Settings, and only to show them next to your mood on your PC. Moodlog never writes to Health Connect."),
        tr("No internet") to tr("Moodlog never connects to the internet. Only the link below opens a page in your browser."),
    )
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(tr("Privacy")) }, navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section(tr("Your data is yours")) {
                for ((title, text) in points) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FilledTonalButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl())))
            }) { Text(tr("Read the full policy")) }
        }
    }
}
