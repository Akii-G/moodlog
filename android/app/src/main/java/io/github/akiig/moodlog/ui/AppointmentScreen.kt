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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.Appointment
import io.github.akiig.moodlog.AppointmentReminder
import io.github.akiig.moodlog.Reminder
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.Gently
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.tr
import io.github.akiig.moodlog.trn
import java.time.LocalTime

/** Before the appointment: when it is, a calm recap since the last one, and the things to talk
 *  about. Opened from the card in the diary or from Events. */
@Composable
fun AppointmentScreen(back: String, onBack: () -> Unit) {
    val store = LocalApp.current.store
    val appt = store.appointment
    val hasDate = appt != null && appt.date.isNotEmpty()
    var date by remember { mutableStateOf(if (hasDate) appt!!.date else Day.adding(7, Day.today)) }
    var time by remember { mutableStateOf(appt?.time ?: "") }
    var with by remember { mutableStateOf(appt?.with ?: "") }
    var saved by remember { mutableStateOf(hasDate) }
    var newPoint by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val today = Day.today
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val app = LocalApp.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var remind by remember { mutableStateOf(AppointmentReminder.LEADS.associateWith { app.prefs.appointmentRemind(it) }) }
    var asking by remember { mutableStateOf<Int?>(null) }
    fun setRemind(lead: Int, on: Boolean) {
        app.prefs.setAppointmentRemind(lead, on)
        remind = remind + (lead to on)
        AppointmentReminder.schedule(context)
    }
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        asking?.let { setRemind(it, ok) }; asking = null
    }

    fun add() {
        store.addPoint(newPoint); newPoint = ""
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = TabBarSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackTitle(back, tr("Before the appointment"), onBack) }
        item {
            Text(tr("When is your next appointment with your doctor or therapist? In the week before, the Log shows how things have gone since the last one."),
                color = muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Section(footer = (if (hasDate) Gently.whenText(appt!!, today) + "\n" else "") +
                        Gently.recap(store, Gently.recapStart(appt, today), today)) {
                    ValueRow(tr("Date"), Day.pretty(date, "EEEE d MMMM yyyy")) { pickDate = true }
                    Line()
                    ValueRow(tr("Time"), time.ifEmpty { tr("Not set") }) { pickTime = true }
                    Line()
                    FieldRow("", with, { with = it; saved = false }, tr("With (optional), e.g. Dr Rossi"),
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    Line()
                    ActionRow(if (saved) tr("Saved") else tr("Save the date"), enabled = !saved) {
                        store.setAppointment(date, time, with); saved = true
                    }
                    if (hasDate) {
                        Line()
                        ActionRow(tr("Remove the appointment"), destructive = true) {
                            store.setAppointment(null); saved = false
                        }
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Section(tr("Remind me"),
                    footer = tr("A notification on this phone: the days before at 18:00, on the day in the morning. It says only that you have an appointment, not with whom.")) {
                    AppointmentReminder.LEADS.forEachIndexed { i, lead ->
                        if (i > 0) Line()
                        SwitchRow(AppointmentReminder.label(lead), remind[lead] == true && Reminder.canNotify(context)) { on ->
                            if (on && !Reminder.canNotify(context) && android.os.Build.VERSION.SDK_INT >= 33) {
                                asking = lead; notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else setRemind(lead, on)
                        }
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Section(tr("Things I want to talk about"),
                    footer = tr("Tick what you've said. The rest can go in the doctor report, if you choose so there.")) {
                    FieldRow("", newPoint, { newPoint = it }, tr("e.g. I sleep badly since the new dose"),
                        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        trailing = {
                            Text(tr("Add"), color = if (newPoint.isBlank()) muted else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = newPoint.isNotBlank()) { add() }
                                    .padding(8.dp))
                        })
                    val sorted = store.points.sortedWith(compareBy({ it.done }, { it.updated }))
                    for (p in sorted) {
                        Line()
                        Row(Modifier.fillMaxWidth().clickable { store.setDone(p, !p.done) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (p.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                if (p.done) tr("Said it") else null, Modifier.size(22.dp),
                                tint = if (p.done) MaterialTheme.colorScheme.primary else muted)
                            Spacer(Modifier.size(12.dp))
                            Text(p.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                color = if (p.done) muted else MaterialTheme.colorScheme.onSurface)
                            IconButton({ store.delete(p) }) { Icon(Icons.Filled.Delete, tr("Delete"), tint = muted) }
                        }
                    }
                }
            }
        }
    }
    if (pickDate) DayPickerDialog(date, allowFuture = true, onDismiss = { pickDate = false }) { date = it; saved = false }
    if (pickTime) TimePickerDialog(if (time.isEmpty()) LocalTime.of(9, 0) else LocalTime.parse(time),
        onDismiss = { pickTime = false }) { time = Day.timeString(it); saved = false }
}

/** In the diary, from a week before the appointment until the day after. */
@Composable
fun AppointmentCard(appointment: Appointment, onOpen: () -> Unit) {
    val store = LocalApp.current.store
    val today = Day.today
    val open = store.openPoints.size
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MedicalServices, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Text(tr("Your appointment") + " · " + Gently.whenText(appointment, today), style = MaterialTheme.typography.titleMedium)
        }
        Text(Gently.recap(store, Gently.recapStart(appointment, today), today), style = MaterialTheme.typography.bodyMedium)
        Text(if (open > 0) trn("%@ thing to talk about", "%@ things to talk about", open)
             else tr("Anything you want to remember to say? Add it here."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Your week, gently": on Sunday evening and on Monday. */
@Composable
fun WeekCard(text: String, onHide: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Text(tr("Your week"), style = MaterialTheme.typography.titleMedium)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Text(tr("Close"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onHide).padding(vertical = 6.dp))
    }
}
