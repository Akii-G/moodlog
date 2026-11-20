package io.github.akiig.moodlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.AppStore
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.EventKind
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.MoodEvent
import io.github.akiig.moodlog.one
import io.github.akiig.moodlog.tr
import io.github.akiig.moodlog.trn

@Composable
fun EventsScreen() {
    val store = LocalApp.current.store
    var editing by remember { mutableStateOf<MoodEvent?>(null) }
    var adding by remember { mutableStateOf(false) }
    val events = store.events.sortedByDescending { it.date }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = TabBarSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LargeTitle(tr("Events")) { CircleButton(Icons.Filled.Add, tr("Add event")) { adding = true } } }
        if (events.isEmpty()) item {
            Text(tr("Log things that can affect how you feel, such as a medication change. They show as a flag in the calendar, and each one compares your mood in the two weeks before and after it."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
        items(events, key = { it.id }) { e ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                Section {
                    Column(Modifier.fillMaxWidth().clickable { editing = e }.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Day.pretty(e.date, "EEEE d MMMM yyyy") + " · " + EventKind.label(e.kind),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(e.title, style = MaterialTheme.typography.titleMedium)
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
            FieldRow("", title, { title = it }, tr("What happened, e.g. new dose"),
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Line()
            ValueRow(tr("Date"), Day.pretty(date, "d MMM yyyy")) { pickDate = true }
            Line()
            Box {
                ValueRow(tr("Type"), EventKind.label(kind)) { kindOpen = true }
                DropdownMenu(kindOpen, { kindOpen = false }) {
                    for (k in EventKind.entries) {
                        DropdownMenuItem(text = { Text(k.label) }, onClick = { kind = k.raw; kindOpen = false })
                    }
                }
            }
        }
        Section(tr("Details (optional)")) {
            FieldRow("", note, { note = it }, tr("Details"), minLines = 3,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        }
        if (original != null) {
            Section { ActionRow(tr("Delete this event"), destructive = true) { store.delete(original); onClose() } }
        }
    }
    if (pickDate) DayPickerDialog(date, allowFuture = true, onDismiss = { pickDate = false }) { date = it }
}
