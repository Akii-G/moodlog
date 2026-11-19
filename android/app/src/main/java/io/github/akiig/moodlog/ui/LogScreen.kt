package io.github.akiig.moodlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akiig.moodlog.AppStore
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.Mood
import io.github.akiig.moodlog.one
import io.github.akiig.moodlog.tr
import io.github.akiig.moodlog.trn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onDay: (String) -> Unit) {
    val app = LocalApp.current
    val store = app.store
    val scope = rememberCoroutineScope()
    var monthText by rememberSaveable { mutableStateOf(Day.monthStart().toString()) }
    val month = LocalDate.parse(monthText)
    val thisMonth = Day.monthStart()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Moodlog") },
            actions = {
                IconButton(onClick = { onDay(Day.today) }) { Icon(Icons.Filled.Add, tr("Today")) }
            },
        )
        PullToRefreshBox(isRefreshing = app.sync.busy, onRefresh = { scope.launch { app.sync.run(store) } },
            modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (app.prefs.server.isBlank()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Computer, null)
                        Spacer(Modifier.size(10.dp))
                        Text(tr("Connect to Moodlog on your PC in Settings to sync."), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                MonthHeader(month, canGoForward = month < thisMonth,
                    onBack = { monthText = month.minusMonths(1).toString() },
                    onForward = { monthText = month.plusMonths(1).toString() })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in Day.weekdayInitials) {
                        Text(d, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Column(Modifier.tourTarget("calendar", app.tour), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (week in Day.grid(month).chunked(7)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in 0 until 7) {
                                val day = week.getOrNull(i)
                                Box(Modifier.weight(1f).aspectRatio(1f)) {
                                    if (day != null) DayCell(store, day) { onDay(day) }
                                }
                            }
                        }
                    }
                }
                Summary(store, month)
                Text(app.sync.status, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun MonthHeader(month: LocalDate, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Day.monthTitle(month), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr("Previous month")) }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Next month"))
        }
    }
}

@Composable
private fun Summary(store: AppStore, month: LocalDate) {
    val days = Day.days(month).filter { store.average(it) != null }
    val values = days.mapNotNull { store.average(it) }
    val text = if (values.isEmpty()) tr("Nothing logged this month yet") else {
        val avg = values.average()
        trn("%@ day logged · average %@ (%@)", "%@ days logged · average %@ (%@)", days.size,
            one(avg), Mood.name(Mood.level(avg)).lowercase())
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One day in the month grid: one colour for one mood, a small grid of tiles for several. */
@Composable
fun DayCell(store: AppStore, day: String, onClick: () -> Unit) {
    val moods = store.moodsOn(day)
    val isToday = day == Day.today
    val future = day > Day.today
    val shape = RoundedCornerShape(12.dp)
    val description = if (moods.isEmpty()) tr("%@, nothing logged", Day.pretty(day))
                      else Day.pretty(day) + ", " + moods.joinToString(", ") { Mood.name(it) }
    Box(
        Modifier.fillMaxSize().clip(shape)
            .background(if (moods.size == 1) Palette.mood(moods[0])
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = if (future) 0.04f else 0.08f))
            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
            .then(if (future) Modifier else Modifier.clickable(onClick = onClick))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (moods.size == 1) {
            Face(moods[0], Modifier.fillMaxSize().padding(start = 9.dp, end = 9.dp, top = 13.dp, bottom = 7.dp),
                Palette.faceInk(moods[0]), outline = false)
        } else if (moods.size > 1) {
            Tiles(moods, Modifier.fillMaxSize().padding(top = 15.dp))
        }
        val ink = if (moods.size == 1) Palette.faceInk(moods[0]) else MaterialTheme.colorScheme.onSurface
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(day.takeLast(2).toInt().toString(), fontSize = 10.sp, color = ink,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.alpha(if (future) 0.35f else 0.85f))
            Spacer(Modifier.weight(1f))
            if (store.eventsOn(day).isNotEmpty()) Icon(Icons.Filled.Flag, null, Modifier.size(8.dp), tint = ink)
            if (store.dreamsOn(day).isNotEmpty()) Icon(Icons.Filled.Bedtime, null, Modifier.size(8.dp), tint = ink)
        }
    }
}

@Composable
private fun Tiles(moods: List<Int>, modifier: Modifier) {
    val shown = moods.take(9)
    val cols = ceil(sqrt(shown.size.toDouble())).toInt()
    val rows = ceil(shown.size / cols.toDouble()).toInt()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until cols) {
                    val i = r * cols + c
                    Box(Modifier.weight(1f).fillMaxSize()
                        .background(if (i < shown.size) Palette.mood(shown[i]) else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center) {
                        if (i < shown.size && rows <= 2) {
                            Face(shown[i], Modifier.fillMaxSize(0.8f), Palette.faceInk(shown[i]), outline = false)
                        }
                    }
                }
            }
        }
    }
}
