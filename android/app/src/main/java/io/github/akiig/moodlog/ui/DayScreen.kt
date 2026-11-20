package io.github.akiig.moodlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.Dream
import io.github.akiig.moodlog.Entry
import io.github.akiig.moodlog.EventKind
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.Mood
import io.github.akiig.moodlog.MoodEvent
import io.github.akiig.moodlog.Scale
import io.github.akiig.moodlog.one
import io.github.akiig.moodlog.tr
import java.time.LocalTime

@Composable
fun DayScreen(day: String, startAdding: Boolean = false, onBack: () -> Unit) {
    val store = LocalApp.current.store
    var editing by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(startAdding) }
    var editingEvent by remember { mutableStateOf<MoodEvent?>(null) }
    var addingEvent by remember { mutableStateOf(false) }
    var editingDream by remember { mutableStateOf<Dream?>(null) }
    var addingDream by remember { mutableStateOf(false) }
    val entries = store.entriesOn(day)
    val today = day == Day.today

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackTitle(tr("Log"), if (today) tr("Today") else Day.pretty(day, "EEE d MMM"), onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            val avg = store.average(day)
            Section(if (avg != null && entries.size > 1) tr("Average %@ · %@", one(avg), Mood.name(Mood.level(avg))) else null) {
                if (entries.isEmpty()) {
                    Text(tr(if (today) "Nothing logged yet today." else "Nothing logged on this day."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    Line()
                }
                for (e in entries) {
                    EntryRow(e) { editing = e }
                    Line()
                }
                ActionRow(tr(if (today) "How do you feel now?" else "Add a mood")) { adding = true }
            }
            Section(tr("Events")) {
                for (e in store.eventsOn(day)) {
                    Column(Modifier.fillMaxWidth().clickable { editingEvent = e }.padding(vertical = 10.dp)) {
                        Text(EventKind.label(e.kind), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(e.title, style = MaterialTheme.typography.bodyLarge)
                    }
                    Line()
                }
                ActionRow(tr("Add an event, e.g. a medication change")) { addingEvent = true }
            }
            Section(tr("Dreams")) {
                for (d in store.dreamsOn(day)) {
                    DreamRow(d) { editingDream = d }
                    Line()
                }
                ActionRow(tr("Add a dream from this night")) { addingDream = true }
            }
            Spacer(Modifier.height(TabBarSpace))
        }
    }

    if (adding) EntryEditor(day, null) { adding = false }
    editing?.let { EntryEditor(day, it) { editing = null } }
    if (addingEvent) EventEditor(null, day) { addingEvent = false }
    editingEvent?.let { EventEditor(it, it.date) { editingEvent = null } }
    if (addingDream) DreamEditor(null, day) { addingDream = false }
    editingDream?.let { DreamEditor(it, it.date) { editingDream = null } }
}

@Composable
fun EntryRow(entry: Entry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(entry.time.ifEmpty { "--:--" }, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (entry.mood > 0) MoodBadge(entry.mood, 34.dp)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (entry.mood > 0) Text(Mood.name(entry.mood), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (entry.energy > 0) Text("E${entry.energy}", style = MaterialTheme.typography.labelMedium, color = Palette.energy, fontWeight = FontWeight.SemiBold)
                if (entry.anxiety > 0) Text("A${entry.anxiety}", style = MaterialTheme.typography.labelMedium, color = Palette.anxiety, fontWeight = FontWeight.SemiBold)
            }
            if (entry.note.isNotEmpty()) {
                Text(entry.note, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EntryEditor(day: String, original: Entry?, onClose: () -> Unit) {
    val store = LocalApp.current.store
    val today = day == Day.today
    var hasTime by remember { mutableStateOf(original?.time?.isNotEmpty() ?: today) }
    var time by remember {
        mutableStateOf(original?.time?.takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) }
            ?: if (today) LocalTime.now() else LocalTime.NOON)
    }
    var mood by remember { mutableIntStateOf(original?.mood ?: 0) }
    var energy by remember { mutableIntStateOf(original?.energy ?: 0) }
    var anxiety by remember { mutableIntStateOf(original?.anxiety ?: 0) }
    var note by remember { mutableStateOf(original?.note ?: "") }
    var pickTime by remember { mutableStateOf(false) }
    val empty = mood == 0 && energy == 0 && anxiety == 0 && note.isBlank()

    EditorDialog(tr(if (original == null) "New entry" else "Edit entry"), !empty, onClose, onSave = {
        val t = if (hasTime) Day.timeString(time) else ""
        if (original != null) store.update(original.copy(time = t, mood = mood, energy = energy, anxiety = anxiety, note = note))
        else store.addEntry(day, t, mood, energy, anxiety, note)
        onClose()
    }) {
        Section(tr("How do you feel?"), padded = false) {
            Column(Modifier.padding(8.dp)) { MoodPicker(mood) { mood = it } }
        }
        Section(tr("Optional"), tr("Energy: 1 very low, 5 very high. Anxiety: 1 calm, 5 very anxious.")) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScalePicker("Energy", Scale.energyWords, Palette.energy, energy) { energy = it }
                ScalePicker("Anxiety", Scale.anxietyWords, Palette.anxiety, anxiety) { anxiety = it }
            }
        }
        Section {
            SwitchRow(tr("Time"), hasTime) { hasTime = it }
            if (hasTime) {
                Line()
                ValueRow(tr("At"), Day.timeString(time)) { pickTime = true }
            }
        }
        Section(tr("Note")) {
            FieldRow("", note, { note = it }, tr("A few words about it"), minLines = 3,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        }
        if (original != null) {
            Section { ActionRow(tr("Delete this entry"), destructive = true) { store.delete(original); onClose() } }
        }
    }
    if (pickTime) TimePickerDialog(time, { pickTime = false }) { time = it }
}
