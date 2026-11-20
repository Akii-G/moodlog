package io.github.akiig.moodlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.Dream
import io.github.akiig.moodlog.DreamFeeling
import io.github.akiig.moodlog.DreamTag
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.tr

/** The dream journal: a summary of the last 30 days, a search, and every dream, newest first. */
@Composable
fun DreamsScreen() {
    val store = LocalApp.current.store
    var editing by remember { mutableStateOf<Dream?>(null) }
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = store.dreams.filter {
        query.isBlank() || it.text.contains(query, ignoreCase = true) || it.details.contains(query, ignoreCase = true)
    }.sortedWith(compareByDescending<Dream> { it.date }.thenByDescending { it.updated })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = TabBarSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LargeTitle(tr("Dreams")) { CircleButton(Icons.Filled.Add, tr("Add a dream")) { adding = true } } }
        if (store.dreams.isEmpty()) {
            item {
                Text(tr("A dream journal. Each dream belongs to the morning you woke up and shows as a moon in the calendar. Write it down soon after waking: dreams fade fast."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
        } else {
            item {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(6.dp))
                    Box(Modifier.weight(1f)) { FieldRow("", query, { query = it }, tr("Search your dreams")) }
                }
            }
            store.dreamSummary(Day.adding(-29, Day.today), Day.today)?.let { summary ->
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        Section(tr("Last 30 days")) {
                            Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
                        }
                    }
                }
            }
        }
        items(shown, key = { it.id }) { d ->
            Box(Modifier.padding(horizontal = 16.dp)) { Section { DreamRow(d) { editing = d } } }
        }
    }
    if (adding) DreamEditor(null, Day.today) { adding = false }
    editing?.let { DreamEditor(it, it.date) { editing = null } }
}

@Composable
fun DreamRow(dream: Dream, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (dream.isNightmare) Icons.Filled.Bedtime else Icons.Outlined.Bedtime, null,
            Modifier.size(22.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(listOf(Day.pretty(dream.date, "EEEE d MMMM yyyy"), dream.details).filter { it.isNotEmpty() }
                .joinToString(" · "), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (dream.text.isNotEmpty()) Text(dream.text, maxLines = 4, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun DreamEditor(original: Dream?, day: String, onClose: () -> Unit) {
    val store = LocalApp.current.store
    var date by remember { mutableStateOf(original?.date ?: day) }
    var feeling by remember { mutableStateOf(original?.feeling ?: "") }
    var tags by remember { mutableStateOf(original?.tags?.toSet() ?: emptySet()) }
    var text by remember { mutableStateOf(original?.text ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    val empty = feeling.isEmpty() && tags.isEmpty() && text.isBlank()

    EditorDialog(tr(if (original == null) "Add a dream" else "Edit dream"), !empty, onClose, onSave = {
        val ordered = DreamTag.entries.map { it.raw }.filter { it in tags }
        val clean = text.trim()
        if (original != null) store.update(original.copy(date = date, feeling = feeling, tags = ordered, text = clean))
        else store.addDream(date, feeling, ordered, clean)
        onClose()
    }) {
        Section {
            ValueRow(tr("Morning of"), Day.pretty(date, "d MMM yyyy")) { pickDate = true }
        }
        Section(tr("How it felt")) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Segmented(listOf("" to "–") + DreamFeeling.entries.map { it.raw to it.label }, feeling) { feeling = it }
            }
        }
        Section(tr("What did you dream?")) {
            FieldRow("", text, { text = it }, tr("A few words, or as much as you remember"), minLines = 5,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        }
        Section(footer = tr("Didn't remember a dream? Turn that on: it counts too.")) {
            DreamTag.entries.forEachIndexed { i, t ->
                if (i > 0) Line()
                SwitchRow(t.label, t.raw in tags) { on -> tags = if (on) tags + t.raw else tags - t.raw }
            }
        }
        if (original != null) {
            Section { ActionRow(tr("Delete this dream"), destructive = true) { store.delete(original); onClose() } }
        }
    }
    if (pickDate) DayPickerDialog(date, allowFuture = false, onDismiss = { pickDate = false }) { date = it }
}
