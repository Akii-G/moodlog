package io.github.akiig.moodlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.AppStore
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.EventKind
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.MoodEvent
import io.github.akiig.moodlog.one
import io.github.akiig.moodlog.tr
import io.github.akiig.moodlog.trn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen() {
    val store = LocalApp.current.store
    var editing by remember { mutableStateOf<MoodEvent?>(null) }
    var adding by remember { mutableStateOf(false) }
    val events = store.events.sortedByDescending { it.date }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(tr("Events")) }, actions = {
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, tr("Add event")) }
        })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (events.isEmpty()) item {
                Text(tr("Log things that can affect how you feel, such as a medication change. They show as a flag in the calendar, and each one compares your mood in the two weeks before and after it."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(events, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().clickable { editing = e },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Day.pretty(e.date, "EEEE d MMMM yyyy") + " · " + EventKind.label(e.kind),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (e.note.isNotEmpty()) Text(e.note, style = MaterialTheme.typography.bodyMedium)
                        Text(eventSummary(store, e), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (adding) EventEditor(null, Day.today) { adding = false }
    editing?.let { EventEditor(it, it.date) { editing = null } }
}

fun eventSummary(store: AppStore, event: MoodEvent): String {
    if (event.date > Day.today) return tr("Planned. Your mood before and after will be compared once it has happened.")
    val around = store.moodAround(event.date)
    fun part(w: AppStore.Window) = if (w.days < 3) tr("not enough days logged (%@)", w.days)
        else trn("%2\$@ (%1\$@ day)", "%2\$@ (%1\$@ days)", w.days, one(w.average))
    var text = tr("Mood in the 2 weeks before: %@ · after: %@", part(around.before), part(around.after))
    if (!around.complete) text += tr(", so far")
    return text
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditor(original: MoodEvent?, day: String, onClose: () -> Unit) {
    val store = LocalApp.current.store
    var date by remember { mutableStateOf(original?.date ?: day) }
    var kind by remember { mutableStateOf(original?.kind ?: EventKind.MEDICATION.raw) }
    var title by remember { mutableStateOf(original?.title ?: "") }
    var note by remember { mutableStateOf(original?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var kindOpen by remember { mutableStateOf(false) }

    EditorDialog(tr(if (original == null) "Add event" else "Edit event"), title.isNotBlank(), onClose, onSave = {
        val clean = title.trim().split(Regex("\\s+")).joinToString(" ")
        if (original != null) store.update(original.copy(date = date, kind = kind, title = clean, note = note))
        else store.addEvent(date, kind, clean, note)
        onClose()
    }) {
        Section {
            ValueRow(tr("Date"), Day.pretty(date, "d MMM yyyy")) { pickDate = true }
            ExposedDropdownMenuBox(kindOpen, { kindOpen = it }) {
                OutlinedTextField(EventKind.label(kind), {}, readOnly = true, label = { Text(tr("Type")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(kindOpen, { kindOpen = false }) {
                    for (k in EventKind.entries) {
                        DropdownMenuItem(text = { Text(k.label) }, onClick = { kind = k.raw; kindOpen = false })
                    }
                }
            }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(tr("What happened, e.g. new dose")) })
        }
        Section(tr("Details (optional)")) {
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 8,
                placeholder = { Text(tr("Details")) })
        }
        if (original != null) {
            TextButton(onClick = { store.delete(original); onClose() }) {
                Text(tr("Delete this event"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (pickDate) DayPickerDialog(date, allowFuture = true, onDismiss = { pickDate = false }) { date = it }
}
