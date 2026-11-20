package io.github.akiig.moodlog.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.Report
import io.github.akiig.moodlog.ReportPeriod
import io.github.akiig.moodlog.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** "Report for your doctor": pick a period and what to include, then share or save the PDF. */
@Composable
fun ReportDialog(onClose: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(ReportPeriod.THREE_MONTHS) }
    var notes by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(true) }
    var dreams by remember { mutableStateOf(false) }
    var dreamText by remember { mutableStateOf(false) }
    var canSleep by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { canSleep = app.health.hasPermissions() }

    suspend fun build(): File {
        val (start, end) = period.range(app.store)
        val nights = if (sleep && canSleep) (try { app.health.nightlySleep(start, end) } catch (e: Exception) { null }) ?: emptyMap() else emptyMap()
        val file = File(context.cacheDir, "reports/" + tr("Moodlog report") + " $start – $end.pdf")
        return withContext(Dispatchers.Default) {
            Report(app.store, start, end, notes, nights, dreams, dreams && dreamText).write(file)
        }
    }

    var built by remember { mutableStateOf<File?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = built ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            message = try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out -> f.inputStream().use { it.copyTo(out) } }
                tr("Saved.")
            } catch (e: Exception) {
                tr("Couldn't save the PDF there.")
            }
        }
    }

    EditorDialog(tr("Report for your doctor"), canSave = false, onCancel = onClose, onSave = {}) {
        Text(tr("A printable PDF with your mood over a period, energy and anxiety, sleep, your events with the mood before and after, and a day-by-day table."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
        Section(tr("Period")) {
            ReportPeriod.entries.forEachIndexed { i, p ->
                if (i > 0) Line()
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { period = p },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (p == period) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Section(tr("Include"), tr("Notes can be private. Leave this off to share only the numbers and events.")) {
            SwitchRow(tr("Include my notes"), notes) { notes = it }
            if (canSleep) {
                Line()
                SwitchRow(tr("Include sleep from Health Connect"), sleep) { sleep = it }
            }
            Line()
            SwitchRow(tr("Include dreams (how many, and nightmare nights)"), dreams) { dreams = it; if (!it) dreamText = false }
            Line()
            SwitchRow(tr("Also what the dreams were about"), dreamText) { dreamText = it; if (it) dreams = true }
        }
        Section(footer = message.ifEmpty { null }) {
            ActionRow(tr("Share PDF…"), busy = busy) {
                busy = true; message = ""
                scope.launch {
                    try {
                        val f = build()
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf")
                            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            tr("Report for your doctor")))
                    } catch (e: Exception) {
                        message = e.localizedMessage ?: e.javaClass.simpleName
                    }
                    busy = false
                }
            }
            Line()
            ActionRow(tr("Save PDF…"), busy = busy) {
                busy = true; message = ""
                scope.launch {
                    try {
                        val f = build()
                        built = f
                        saver.launch(f.name)
                    } catch (e: Exception) {
                        message = e.localizedMessage ?: e.javaClass.simpleName
                    }
                    busy = false
                }
            }
        }
    }
}
