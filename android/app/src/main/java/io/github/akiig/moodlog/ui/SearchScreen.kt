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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.EventKind
import io.github.akiig.moodlog.Activity
import io.github.akiig.moodlog.Feeling
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.Mood
import io.github.akiig.moodlog.Search
import io.github.akiig.moodlog.tr

/** Opened from the magnifying glass in the diary: every note to read back, or a search through
 *  notes, feeling words and events. A result opens its day. */
@Composable
fun SearchScreen(onDay: (String) -> Unit, onBack: () -> Unit) {
    val store = LocalApp.current.store
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val hits = Search.results(store.entries, store.events, q)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = TabBarSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackTitle(tr("Log"), tr("Search"), onBack) }
        item {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                Box(Modifier.weight(1f)) { FieldRow("", query, { query = it }, tr("Search notes, feelings, activities and events")) }
            }
        }
        if (hits.isEmpty()) item {
            Text(if (q.isEmpty()) tr("No notes yet. Add a few words when you log a mood and they will show up here.")
                 else tr("Nothing matches “%@”.", q),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
        items(hits.take(300), key = { it.key }) { hit ->
            Box(Modifier.padding(horizontal = 16.dp)) { Section { SearchRow(hit, q) { onDay(hit.date) } } }
        }
    }
}

/** The text with every match in bold. */
fun marked(text: String, query: String): AnnotatedString = buildAnnotatedString {
    append(text)
    for (r in Search.find(text, query)) addStyle(SpanStyle(fontWeight = FontWeight.Bold), r.first, r.last + 1)
}

@Composable
fun SearchRow(hit: Search.Hit, query: String, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (hit) {
            is Search.Hit.OfEntry -> {
                val e = hit.entry
                Box(Modifier.size(30.dp)) { if (e.mood > 0) MoodBadge(e.mood, 30.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(listOf(Day.pretty(e.date, "EEEE d MMMM yyyy"), e.time, if (e.mood > 0) Mood.name(e.mood) else "")
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = muted)
                    if (e.note.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (e.private && LocalApp.current.privateTick) Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = muted)
                        Text(marked(e.note, query), maxLines = 6, style = MaterialTheme.typography.bodyLarge)
                    }
                    val words = Feeling.text(e.feelings)
                    if (words.isNotEmpty() && (e.note.isEmpty() || Search.matches(words, query)))
                        Text(marked(words, query), style = MaterialTheme.typography.bodyMedium, color = muted)
                    val acts = Activity.text(e.activities)
                    if (acts.isNotEmpty() && (e.note.isEmpty() || Search.matches(acts, query)))
                        Text(marked(acts, query), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
            is Search.Hit.OfEvent -> {
                val ev = hit.event
                Icon(Icons.Filled.Flag, null, Modifier.size(30.dp).padding(4.dp), tint = muted)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(Day.pretty(ev.date, "EEEE d MMMM yyyy") + " · " + EventKind.label(ev.kind),
                        style = MaterialTheme.typography.labelMedium, color = muted)
                    Text(marked(ev.title, query), style = MaterialTheme.typography.bodyLarge)
                    if (ev.note.isNotEmpty()) Text(marked(ev.note, query), maxLines = 4,
                        style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
        }
    }
}
