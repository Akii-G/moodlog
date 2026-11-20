package io.github.akiig.moodlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.WhatsNew
import io.github.akiig.moodlog.tr

/** Every version of this app, newest first; versions after `since` are marked as new. */
@Composable
fun WhatsNewDialog(since: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val versions = remember { WhatsNew.load(context) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp)) {
                    Text(tr("What's new"), Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(tr("Done"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    for (v in versions) {
                        val isNew = since != null && WhatsNew.newer(v.version, since)
                        Section(tr("Version %@", v.version) + " · " + Day.pretty(v.date, "d MMMM yyyy")) {
                            if (isNew) {
                                Row(Modifier.padding(top = 10.dp)) {
                                    Text(tr("New"), color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            v.items.forEachIndexed { i, item ->
                                if (i > 0) Line()
                                Row(Modifier.padding(vertical = 10.dp)) {
                                    Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
                                    Text(item, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
