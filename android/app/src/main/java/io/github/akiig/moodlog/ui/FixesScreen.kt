package io.github.akiig.moodlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.Corrections
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.tr

/** Nights under 4 hours, to delete the ones when the watch was charging, not worn. A deleted
 *  night goes away here and on the PC (with the sync) and never comes back from Health Connect. */
@Composable
fun ShortNightsDialog(onClose: () -> Unit) {
    val app = LocalApp.current
    val store = app.store
    var all by remember { mutableStateOf<Map<String, Double>?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        all = try { app.health.nightlySleep(Day.adding(-365, Day.today), Day.today) } catch (e: Exception) { null } ?: emptyMap()
    }
    val gone = store.corrections.deletedNights
    val nights = (all ?: emptyMap()).filter { it.value < 4 && it.key !in gone }.toSortedMap(reverseOrder())
    EditorDialog(tr("Short nights"), canSave = false, onCancel = onClose, onSave = {}) {
        Text(tr("Nights under 4 hours. If you weren't wearing your watch (for example while it was charging), delete that night: it goes away from Sottovoce here and on your PC, and won't come back with the next sync. Real short nights are worth keeping: they matter to your doctor."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
        Section {
            when {
                all == null -> Text(tr("Reading Health Connect…"), Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                nights.isEmpty() -> Text(tr("No nights under 4 hours."), Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> nights.entries.forEachIndexed { i, (day, hours) ->
                    if (i > 0) Line()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Day.pretty(day, "EEE d MMM yyyy"), style = MaterialTheme.typography.bodyLarge)
                            Text("%.1f h".format(hours), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { asking = day }) {
                            Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    asking?.let { day ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(tr("Delete this night?")) },
            text = { Text(tr("This night's sleep will be deleted from Sottovoce here and on your PC. Health Connect keeps its own copy.")) },
            confirmButton = {
                TextButton(onClick = { store.deleteNight(day); asking = null }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(tr("Cancel")) } },
        )
    }
}

/** One medicine under several names in Dose: give it one name, and every dose shows under it
 *  here and on the PC. */
@Composable
fun MedicineNamesDialog(onClose: () -> Unit) {
    val store = LocalApp.current.store
    val raw = store.localDose ?: store.doseFeed
    val renames = store.corrections.renames()
    // every name as logged, with its first and last day
    val logged = HashMap<String, Pair<String, String>>()
    for (d in raw?.doses ?: emptyList()) {
        if (d.status == "pending") continue
        val (a, b) = logged[d.name] ?: (d.date to d.date)
        logged[d.name] = minOf(a, d.date) to maxOf(b, d.date)
    }
    val ingredients = (raw?.medicines ?: emptyList()).associate { it.name to it.ingredient }
    val groups = Corrections.suggestions(logged.keys, ingredients, renames)
    val renamed = renames.keys.groupBy { renames.getValue(it) }
    val free = logged.keys.filter { it !in renames }.sortedBy { it.lowercase() }
    val groupNames = remember { mutableStateMapOf<String, String>() }
    val picked = remember { mutableStateMapOf<String, Boolean>() }
    var otherName by remember { mutableStateOf("") }

    fun newest(names: List<String>) = names.maxByOrNull { logged[it]?.second ?: "" } ?: ""

    @Composable
    fun nameText(name: String, modifier: Modifier = Modifier) {
        Column(modifier) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            logged[name]?.let { (a, b) ->
                Text("Dose · " + if (a == b) Day.pretty(a, "d MMM") else Day.pretty(a, "d MMM") + " – " + Day.pretty(b, "d MMM yyyy"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    fun nameField(value: String, onValue: (String) -> Unit, names: List<String>) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value, onValue, Modifier.weight(1f), singleLine = true,
                placeholder = { Text(tr("Name to show in the report")) })
            Button(onClick = {
                val to = value.trim()
                if (to.isNotEmpty()) { store.renameMedicines(names, to); picked.clear(); otherName = "" }
            }, enabled = value.isNotBlank()) { Text(tr("Use this name")) }
        }
    }

    EditorDialog(tr("Medicine names"), canSave = false, onCancel = onClose, onSave = {}) {
        Text(tr("If you logged one medicine under different names (for example in Apple Health and then in Dose), give them one name: all its doses then show under it, here and on your PC. Don't do it for two strengths if your dose really changed."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
        groups.forEachIndexed { gi, group ->
            Section(if (gi == 0) tr("These look like the same medicine") else null) {
                group.forEachIndexed { i, name ->
                    if (i > 0) Line()
                    nameText(name, Modifier.padding(vertical = 10.dp))
                }
                Line()
                val key = group.joinToString("|")
                nameField(groupNames[key] ?: newest(group), { groupNames[key] = it }, group)
            }
        }
        if (renamed.isNotEmpty()) {
            Section(tr("Renamed")) {
                renamed.keys.sortedBy { it.lowercase() }.forEachIndexed { i, to ->
                    if (i > 0) Line()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(to, style = MaterialTheme.typography.bodyLarge)
                            Text(renamed.getValue(to).sorted().joinToString(", "), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { store.undoRename(to) }) { Text(tr("Undo")) }
                    }
                }
            }
        }
        if (free.isNotEmpty()) {
            Section(tr("Other names"), tr("Tick two or more names, then give them one name.")) {
                free.forEachIndexed { i, name ->
                    if (i > 0) Line()
                    val on = picked[name] == true
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { picked[name] = !on },
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
                            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp))
                        nameText(name, Modifier.weight(1f))
                    }
                }
                val chosen = picked.filterValues { it }.keys.toList()
                if (chosen.size >= 2) {
                    Line()
                    nameField(otherName, { otherName = it }, chosen)
                }
            }
        }
        if (groups.isEmpty() && renamed.isEmpty() && free.isEmpty()) EmptyText(tr("No doses yet."))
    }
}
