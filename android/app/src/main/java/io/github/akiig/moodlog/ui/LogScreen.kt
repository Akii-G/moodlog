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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import io.github.akiig.moodlog.Care
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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

    PullToRefreshBox(isRefreshing = app.sync.busy, onRefresh = { scope.launch { app.sync.run(store) } },
        modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            LargeTitle("Moodlog") { CircleButton(Icons.Filled.Add, tr("Today")) { onDay(Day.today) } }
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var hidden by remember { mutableStateOf(false) }
                if (!hidden && Care.shouldShow(store, app.prefs)) KindWord {
                    app.prefs.kindDismissed = Day.today; hidden = true
                }
                if (app.prefs.server.isBlank()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Computer, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Text(tr("Connect to Moodlog on your PC in Settings to sync."), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                MonthHeader(month, canGoForward = month < thisMonth,
                    onBack = { monthText = month.minusMonths(1).toString() },
                    onForward = { monthText = month.plusMonths(1).toString() })
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (d in Day.weekdayInitials) {
                        Text(d, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
                Column(Modifier.tourTarget("calendar", app.tour), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (week in Day.grid(month).chunked(7)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            for (i in 0 until 7) {
                                val day = week.getOrNull(i)
                                Box(Modifier.weight(1f).aspectRatio(0.78f)) {
                                    if (day != null) DayCell(store, day) { onDay(day) }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Summary(store, month)
                    Text(app.sync.status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                }
                Spacer(Modifier.height(TabBarSpace))
            }
        }
    }
}

/** After several very hard days: a calm note with a helpline. Hidden for a week when dismissed. */
@Composable
fun KindWord(onHide: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, null, tint = Palette.mood(1).copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Text(tr("Some hard days lately"), style = MaterialTheme.typography.titleMedium)
        }
        Text(tr("That sounds heavy. You don't have to carry it alone: talking to someone can help, even just once."),
            style = MaterialTheme.typography.bodyMedium)
        for (h in Care.helplines()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable {
                    val intent = if (h.number != null) Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + h.number.replace(" ", "")))
                                 else Intent(Intent.ACTION_VIEW, Uri.parse(h.link))
                    try { context.startActivity(intent) } catch (e: Exception) { }
                }
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(h.name + (h.number?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text(h.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (h.number != null) Icons.Filled.Call else Icons.AutoMirrored.Filled.OpenInNew, null,
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(tr("Thanks, hide this for now"), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onHide).padding(vertical = 6.dp))
    }
}

@Composable
fun MonthHeader(month: LocalDate, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
        Text(Day.monthTitle(month), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr("Previous month"), Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Next month"), Modifier.size(30.dp),
                tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
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
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * One day in the month grid. The date, the flag (events) and the moon (dreams) sit in their
 * own strip at the top; below it the face, or a small grid of tiles when there are several
 * moods that day. The cell's text doesn't grow with the phone's font size, so it always fits;
 * the screen reader still reads the full date and moods.
 */
@Composable
fun DayCell(store: AppStore, day: String, onClick: () -> Unit) {
    val app = LocalApp.current
    val all = store.moodsOn(day)
    val latestOnly = app.several == "latest" && all.size > 1
    val moods = if (latestOnly) listOf(all.last()) else all
    val isToday = day == Day.today
    val future = day > Day.today
    val shape = RoundedCornerShape(12.dp)
    val single = moods.size == 1
    val description = if (all.isEmpty()) tr("%@, nothing logged", Day.pretty(day))
                      else Day.pretty(day) + ", " + all.joinToString(", ") { Mood.name(it) }
    val ink = if (single) Palette.faceInk(moods[0]) else MaterialTheme.colorScheme.onSurface
    val fixed = Density(LocalDensity.current.density, 1f)
    Column(
        Modifier.fillMaxSize().clip(shape)
            .background(when {
                single -> Palette.mood(moods[0])
                future -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceContainer
            })
            .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
            .then(if (future) Modifier else Modifier.clickable(onClick = onClick))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides fixed) {
            Row(Modifier.fillMaxWidth().height(17.dp).padding(start = 6.dp, end = 5.dp, top = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(day.takeLast(2).toInt().toString(), fontSize = 11.sp, color = ink, maxLines = 1,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.alpha(if (future) 0.35f else 0.9f))
                Spacer(Modifier.weight(1f))
                if (store.eventsOn(day).isNotEmpty()) Icon(Icons.Filled.Flag, null, Modifier.size(9.dp), tint = ink)
                if (app.showDreams && store.dreamsOn(day).isNotEmpty()) Icon(Icons.Filled.Bedtime, null, Modifier.size(9.dp), tint = ink)
                if (latestOnly) Text("×" + all.size, fontSize = 9.sp, color = ink, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, modifier = Modifier.alpha(0.8f))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (single) {
                Face(moods[0], Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 2.dp),
                    ink, outline = false)
            } else if (moods.size > 1) {
                Tiles(moods, Modifier.fillMaxSize().padding(start = 3.dp, end = 3.dp, bottom = 3.dp))
            }
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
                    Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp))
                        .background(if (i < shown.size) Palette.mood(shown[i]) else Color.Transparent),
                        contentAlignment = Alignment.Center) {
                        if (i < shown.size) {
                            Face(shown[i], Modifier.fillMaxSize(if (rows <= 2) 0.78f else 0.9f),
                                Palette.faceInk(shown[i]), outline = false)
                        }
                    }
                }
            }
        }
    }
}
