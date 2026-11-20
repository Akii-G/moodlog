package io.github.akiig.moodlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.AppStore
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.Mood
import io.github.akiig.moodlog.one
import io.github.akiig.moodlog.tr
import io.github.akiig.moodlog.trn
import java.time.LocalDate

@Composable
fun ChartsScreen() {
    val app = LocalApp.current
    val store = app.store
    var report by remember { mutableStateOf(false) }
    var monthText by rememberSaveable { mutableStateOf(Day.monthStart().toString()) }
    val month = LocalDate.parse(monthText)
    val days = Day.days(month)
    val moodPoints = days.mapIndexedNotNull { i, d -> store.average(d)?.let { i to it } }
    val energy = days.mapIndexedNotNull { i, d -> store.scaleAverage(d, true)?.let { i to it } }
    val anxiety = days.mapIndexedNotNull { i, d -> store.scaleAverage(d, false)?.let { i to it } }
    val eventDays = store.events.mapNotNull { e -> days.indexOf(e.date).takeIf { it >= 0 } }
    val allMoods = days.flatMap { store.moodsOn(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle(tr("Charts")) {
            CircleButton(Icons.Outlined.Description, tr("Report for your doctor")) { report = true }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MonthHeader(month, month < Day.monthStart(), { monthText = month.minusMonths(1).toString() },
                { monthText = month.plusMonths(1).toString() })
            Tiles(store, moodPoints.map { it.second }, allMoods)

            ChartCard(tr("Mood day by day")) {
                if (moodPoints.isEmpty()) EmptyText(tr("No moods logged this month yet"))
                else MoodLine(moodPoints, eventDays, days.size)
            }
            ChartCard(tr("Energy and anxiety (1–5)")) {
                if (energy.isEmpty() && anxiety.isEmpty()) EmptyText(tr("No energy or anxiety logged this month yet"))
                else {
                    ScaleLines(energy, anxiety, days.size)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(Palette.energy, tr("Energy"))
                        Legend(Palette.anxiety, tr("Anxiety"))
                    }
                    Text(tr("Energy: 1 very low, 5 very high. Anxiety: 1 calm, 5 very anxious."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SleepAndMood(month, days)
            ChartCard(tr("How often you logged each mood")) {
                if (allMoods.isEmpty()) EmptyText(tr("Nothing logged this month yet"))
                else Bars(allMoods)
            }
            Spacer(Modifier.height(TabBarSpace))
        }
    }
    if (report) ReportDialog { report = false }
}

/** Sleep from Health Connect night by night, with the day's mood on top of each bar, and the
 *  mood after shorter vs longer nights. */
@Composable
private fun SleepAndMood(month: LocalDate, days: List<String>) {
    val app = LocalApp.current
    val store = app.store
    var sleep by remember(month) { mutableStateOf<Map<String, Double>?>(null) }
    var asked by remember(month) { mutableStateOf(false) }
    LaunchedEffect(month) {
        sleep = try { app.health.nightlySleep(days.first(), days.last()) } catch (e: Exception) { null }
        asked = true
    }
    if (!asked) return
    ChartCard(tr("Sleep and mood")) {
        val nights = sleep
        when {
            nights == null -> Text(tr("Allow Health Connect in Settings to see your sleep here, next to your mood."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            nights.isEmpty() -> EmptyText(tr("No sleep in Health Connect this month"))
            else -> {
                val top = (nights.values.max().coerceAtLeast(9.0) / 3).let { kotlin.math.ceil(it) * 3 }
                val bar = Palette.health
                val ringColor = MaterialTheme.colorScheme.surfaceContainer
                val grid = MaterialTheme.colorScheme.outlineVariant
                val moodColors = (1..5).associateWith { Palette.mood(it) }
                Row(Modifier.fillMaxWidth().height(170.dp)) {
                    Column(Modifier.fillMaxHeight().padding(vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        for (h in listOf(top, top * 2 / 3, top / 3, 0.0)) Text("${h.toInt()} h", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 14.dp)) {
                        val y = { v: Double -> (size.height - v / top * size.height).toFloat() }
                        for (k in 0..3) drawLine(grid, Offset(0f, y(top * k / 3)), Offset(size.width, y(top * k / 3)), 1f)
                        val step = size.width / days.size
                        val w = (step * 0.62f).coerceAtMost(14.dp.toPx())
                        days.forEachIndexed { i, d ->
                            val h = nights[d] ?: return@forEachIndexed
                            val x = step * (i + 0.5f)
                            drawRoundRect(bar, Offset(x - w / 2, y(h)), androidx.compose.ui.geometry.Size(w, y(0.0) - y(h)),
                                androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()))
                            store.average(d)?.let { m ->
                                drawCircle(ringColor, 5.5.dp.toPx(), Offset(x, y(h) - 7.dp.toPx()))
                                drawCircle(moodColors[Mood.level(m)]!!, 4.dp.toPx(), Offset(x, y(h) - 7.dp.toPx()))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(bar, tr("Hours asleep"))
                    Legend(Palette.mood(4), tr("Mood that day"))
                }
                val short = nights.filter { it.value < 7 }.keys.mapNotNull { store.average(it) }
                val long = nights.filter { it.value >= 7 }.keys.mapNotNull { store.average(it) }
                val avg = tr("Average %@ h a night (%@ nights).", one(nights.values.average()), nights.size)
                val compare = if (short.size >= 3 && long.size >= 3)
                    " " + tr("Mood after nights under 7 h: %@; after 7 h or more: %@. A pattern in your data, not a cause.",
                        one(short.average()), one(long.average())) else ""
                Text(avg + compare, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Tiles(store: AppStore, averages: List<Double>, allMoods: List<Int>) {
    val avg = averages.takeIf { it.isNotEmpty() }?.average()
    val common = (1..5).maxWithOrNull(compareBy<Int> { l -> allMoods.count { it == l } }.thenBy { it }) ?: 3
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(tr("Average mood"), avg?.let { one(it) } ?: "–", avg?.let { Mood.name(Mood.level(it)) } ?: tr("nothing logged"))
            Tile(tr("Days logged"), "${averages.size}", tr("this month"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(tr("Most common"), if (allMoods.isEmpty()) "–" else Mood.name(common),
                if (allMoods.isEmpty()) "" else trn("%@ time", "%@ times", allMoods.count { it == common }))
            Tile(tr("Current streak"), trn("%@ day", "%@ days", store.streak), tr("in a row"))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(title: String, value: String, sub: String) {
    Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Y labels on the left (mood names or 1-5), days 1 … end along the bottom. */
@Composable
private fun Plot(labels: List<String>, dayCount: Int, height: Int, description: String,
                 draw: androidx.compose.ui.graphics.drawscope.DrawScope.(x: (Int) -> Float, y: (Double) -> Float) -> Unit) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(height.dp).semantics { contentDescription = description }) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            for (l in labels.reversed()) Text(l, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)) {
            val x = { i: Int -> if (dayCount <= 1) size.width / 2 else i * size.width / (dayCount - 1) }
            val y = { v: Double -> (size.height - (v - 1) / 4 * size.height).toFloat() }
            for (v in 1..5) drawLine(grid, Offset(0f, y(v.toDouble())), Offset(size.width, y(v.toDouble())), 1f)
            draw(x, y)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 40.dp)) {
        Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("$dayCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoodLine(points: List<Pair<Int, Double>>, events: List<Int>, dayCount: Int) {
    val colors = points.map { Palette.mood(Mood.level(it.second)) }
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.surfaceContainer
    val desc = points.joinToString(", ") { "${it.first + 1}: ${one(it.second)}" }
    Plot((1..5).map { Mood.name(it) }, dayCount, 220, desc) { x, y ->
        for (e in events) {
            drawLine(line.copy(alpha = 0.6f), Offset(x(e), 0f), Offset(x(e), size.height), 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        for (k in 1 until points.size) {
            val (a, va) = points[k - 1]
            val (b, vb) = points[k]
            drawLine(line.copy(alpha = 0.7f), Offset(x(a), y(va)), Offset(x(b), y(vb)), 2.dp.toPx(), StrokeCap.Round)
        }
        points.forEachIndexed { k, (i, v) ->
            drawCircle(ring, 6.dp.toPx(), Offset(x(i), y(v)))
            drawCircle(colors[k], 4.5.dp.toPx(), Offset(x(i), y(v)))
        }
    }
}

@Composable
private fun ScaleLines(energy: List<Pair<Int, Double>>, anxiety: List<Pair<Int, Double>>, dayCount: Int) {
    val e = Palette.energy
    val a = Palette.anxiety
    val ring = MaterialTheme.colorScheme.surfaceContainer
    Plot((1..5).map { "$it" }, dayCount, 180, tr("Energy and anxiety (1–5)")) { x, y ->
        for ((series, color) in listOf(energy to e, anxiety to a)) {
            for (k in 1 until series.size) {
                drawLine(color, Offset(x(series[k - 1].first), y(series[k - 1].second)),
                    Offset(x(series[k].first), y(series[k].second)), 2.dp.toPx(), StrokeCap.Round)
            }
            for ((i, v) in series) {
                drawCircle(ring, 5.5.dp.toPx(), Offset(x(i), y(v)))
                drawCircle(color, 4.dp.toPx(), Offset(x(i), y(v)))
            }
        }
    }
}

@Composable
private fun Bars(moods: List<Int>) {
    val most = (1..5).maxOf { l -> moods.count { it == l } }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (level in 5 downTo 1) {
            val n = moods.count { it == level }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Mood.name(level), Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Box(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(n / most.toFloat()).height(22.dp)
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(Palette.mood(level)))
                }
                Text("$n", Modifier.width(32.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
