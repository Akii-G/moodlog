package io.github.akiig.moodlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(day: String, onBack: () -> Unit) {
    val store = LocalApp.current.store
    var editing by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<MoodEvent?>(null) }
    var addingEvent by remember { mutableStateOf(false) }
    var editingDream by remember { mutableStateOf<Dream?>(null) }
    var addingDream by remember { mutableStateOf(false) }
    val entries = store.entriesOn(day)
    val today = day == Day.today

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (today) tr("Today") else Day.pretty(day, "EEE d MMM")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back")) } },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val avg = store.average(day)
            Section(if (avg != null && entries.size > 1) tr("Average %@ · %@", one(avg), Mood.name(Mood.level(avg))) else null) {
                if (entries.isEmpty()) {
                    Text(tr(if (today) "Nothing logged yet today." else "Nothing logged on this day."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (e in entries) EntryRow(e) { editing = e }
                AddButton(tr(if (today) "How do you feel now?" else "Add a mood"), Icons.Filled.AddCircle) { adding = true }
            }
            Section(tr("Events")) {
                for (e in store.eventsOn(day)) {
                    Column(Modifier.fillMaxWidth().clickable { editingEvent = e }.padding(vertical = 4.dp)) {
                        Text(EventKind.label(e.kind), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(e.title)
                    }
                }
                AddButton(tr("Add an event, e.g. a medication change"), Icons.Outlined.Flag) { addingEvent = true }
            }
            Section(tr("Dreams")) {
                for (d in store.dreamsOn(day)) DreamRow(d) { editingDream = d }
                AddButton(tr("Add a dream from this night"), Icons.Outlined.Bedtime) { addingDream = true }
            }
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
fun AddButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Icon(icon, null)
        Spacer(Modifier.size(8.dp))
        Text(text)
    }
}

@Composable
fun EntryRow(entry: Entry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(entry.time.ifEmpty { "--:--" }, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (entry.mood > 0) MoodBadge(entry.mood, 30.dp)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (entry.mood > 0) Text(Mood.name(entry.mood), fontWeight = FontWeight.Medium)
                if (entry.energy > 0) Text("E${entry.energy}", style = MaterialTheme.typography.labelMedium, color = Palette.energy)
                if (entry.anxiety > 0) Text("A${entry.anxiety}", style = MaterialTheme.typography.labelMedium, color = Palette.anxiety)
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
        Section(tr("How do you feel?")) { MoodPicker(mood) { mood = it } }
        Section(tr("Optional"), tr("Energy: 1 very low, 5 very high. Anxiety: 1 calm, 5 very anxious.")) {
            ScalePicker("Energy", Scale.energyWords, Palette.energy, energy) { energy = it }
            ScalePicker("Anxiety", Scale.anxietyWords, Palette.anxiety, anxiety) { anxiety = it }
        }
        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Time"), Modifier.weight(1f))
                Switch(hasTime, { hasTime = it })
            }
            if (hasTime) ValueRow(tr("At"), Day.timeString(time)) { pickTime = true }
        }
        Section(tr("Note")) {
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 10,
                placeholder = { Text(tr("A few words about it")) })
        }
        if (original != null) {
            TextButton(onClick = { store.delete(original); onClose() }) {
                Text(tr("Delete this entry"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (pickTime) TimePickerDialog(time, { pickTime = false }) { time = it }
}
