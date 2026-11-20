package io.github.akiig.moodlog.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import io.github.akiig.moodlog.BuildConfigVersion
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.SyncException
import io.github.akiig.moodlog.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onPrivacy: () -> Unit) {
    val app = LocalApp.current
    val prefs = app.prefs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(prefs.server) }
    var token by remember { mutableStateOf(prefs.token) }
    var showToken by remember { mutableStateOf(false) }
    var autoSync by remember { mutableStateOf(prefs.autoSync) }
    var sendHealth by remember { mutableStateOf(prefs.sendHealth) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var allowed by remember { mutableStateOf(false) }
    val healthAvailable = app.health.isAvailable()
    val ready = server.isNotBlank() && token.isNotBlank()

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()) { granted ->
        allowed = granted.containsAll(app.health.permissions)
    }
    LaunchedEffect(Unit) { allowed = app.health.hasPermissions() }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        LargeTitle(tr("Settings"))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Section(tr("Your PC"), tr("In Moodlog on your PC, open Phone sync and switch the receiver on. Use the address and token shown there. Your phone and PC need to be on the same Wi-Fi.")) {
                FieldRow(tr("Address"), server, { server = it; prefs.server = it.trim() }, "http://pop-os.local:8765",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
                Line()
                FieldRow(tr("Token"), token, { token = it; prefs.token = it.trim() }, tr("From Phone sync on your PC"),
                    secret = !showToken,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)) {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            tr(if (showToken) "Hide token" else "Show token"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Line()
                ActionRow(tr("Test connection"), enabled = ready, busy = testing) {
                    testing = true
                    scope.launch {
                        testResult = try {
                            withContext(Dispatchers.IO) { app.client.ping() }
                        } catch (e: SyncException) {
                            e.message ?: ""
                        } catch (e: Exception) {
                            e.localizedMessage ?: e.javaClass.simpleName
                        }
                        testing = false
                    }
                }
                if (testResult.isNotEmpty()) {
                    Text(testResult, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                }
            }

            Section(tr("Sync"), tr("When automatic, Moodlog syncs when you open it and a few seconds after each change. Entries made away from home are kept on the phone and sync when you're back.")) {
                SwitchRow(tr("Sync automatically"), autoSync) { autoSync = it; prefs.autoSync = it }
                Line()
                ActionRow(tr("Sync now"), enabled = ready, busy = app.sync.busy) { scope.launch { app.sync.run(app.store) } }
                Text(app.sync.status, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            }

            Section(tr("Health Connect"), tr("Sends sleep, steps and resting heart rate from Health Connect to Moodlog on your PC, so you can compare them with your mood. Your watch or fitness app needs to share them with Health Connect.")) {
                if (!healthAvailable) {
                    Text(tr("Health Connect isn't on this phone yet."), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 10.dp))
                    Line()
                    ActionRow(tr("Get Health Connect")) {
                        val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
                        } catch (e: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                        }
                    }
                } else {
                    if (!allowed) {
                        ActionRow(tr("Allow access to steps, heart rate and sleep")) { permissionLauncher.launch(app.health.permissions) }
                    } else {
                        Text(tr("Moodlog can read your steps, resting heart rate and sleep."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp))
                    }
                    Line()
                    SwitchRow(tr("Send with each sync (at most hourly)"), sendHealth) { sendHealth = it; prefs.sendHealth = it }
                    Line()
                    ActionRow(tr("Send the last 7 days now"), enabled = ready && allowed) { scope.launch { app.sync.sendHealth() } }
                    if (app.sync.healthStatus.isNotEmpty()) {
                        Text(app.sync.healthStatus, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
            }

            Section {
                LinkRow(tr("Show the tour")) { app.tour.start() }
                if (Build.VERSION.SDK_INT >= 33) {
                    Line()
                    LinkRow(tr("Language")) {
                        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)))
                    }
                }
                Line()
                LinkRow(tr("Privacy"), onClick = onPrivacy)
            }
            Section(tr("About")) {
                ValueRow(tr("Version"), BuildConfigVersion.name(context))
                Line()
                ValueRow(tr("Entries on this phone"), "${app.store.entries.size}")
                Line()
                ValueRow(tr("Events"), "${app.store.events.size}")
                Line()
                ValueRow(tr("Dreams"), "${app.store.dreams.size}")
            }
            Spacer(Modifier.height(TabBarSpace))
        }
    }
}
