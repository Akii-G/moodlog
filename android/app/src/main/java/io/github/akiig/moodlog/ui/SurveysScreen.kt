package io.github.akiig.moodlog.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.Reminder
import io.github.akiig.moodlog.Survey
import io.github.akiig.moodlog.SurveyKind
import io.github.akiig.moodlog.SurveyReminder
import io.github.akiig.moodlog.SurveyText
import io.github.akiig.moodlog.tr
import io.github.akiig.moodlog.trn

/** Questionnaires: answer PHQ-9 or GAD-7, see the scores over time, set the two-week reminder. */
@Composable
fun SurveysScreen(back: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val store = app.store
    val context = LocalContext.current
    var answering by remember { mutableStateOf<SurveyKind?>(null) }
    var care by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Survey?>(null) }
    var remind by remember { mutableStateOf(app.prefs.surveyRemind) }
    fun setRemind(on: Boolean) {
        remind = on; app.prefs.surveyRemind = on; SurveyReminder.schedule(context)
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { setRemind(true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackTitle(back, tr("Questionnaires"), onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Section(footer = tr("Two short questionnaires doctors use: PHQ-9 about low mood and GAD-7 about anxiety. Each one asks about the last two weeks. Sottovoce shows only your score, and the report can include them for your doctor.")) {
                SurveyKind.entries.forEachIndexed { i, kind ->
                    if (i > 0) Line()
                    Row(Modifier.fillMaxWidth().clickable { answering = kind }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Answer %@", kind.title), style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(kind.text.about, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Section(footer = tr("A notification two weeks after your last questionnaire. Nothing is sent before your first one.")) {
                SwitchRow(tr("Remind me every two weeks"), remind) { on ->
                    if (on && !Reminder.canNotify(context) && Build.VERSION.SDK_INT >= 33)
                        notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else setRemind(on)
                }
            }
            if (store.surveys.isNotEmpty()) {
                Section(tr("Scores over time")) {
                    SurveyChart(store.surveys, Modifier.fillMaxWidth().height(190.dp).padding(vertical = 12.dp))
                }
                Section(tr("History"), footer = tr("Tap one to delete it.")) {
                    store.surveys.reversed().forEachIndexed { i, s ->
                        if (i > 0) Line()
                        Row(Modifier.fillMaxWidth().clickable { toDelete = s }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("%@ · %@ of %@", s.name, "${s.score}", "${s.maxScore}"),
                                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(details(s), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (s.private) Icon(Icons.Filled.Lock, tr("Private"), Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(SurveyText.current.credit, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(TabBarSpace))
        }
    }
    answering?.let { kind ->
        SurveyAnswerDialog(kind, onCancel = { answering = null }) { survey ->
            answering = null
            if (survey.worries) care = true
        }
    }
    if (care) SurveyCareDialog { care = false }
    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(tr("Delete this questionnaire?")) },
            text = { Text(tr("It's deleted on your other devices too at the next sync.")) },
            confirmButton = {
                TextButton(onClick = { store.delete(s); toDelete = null }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("Cancel")) } },
        )
    }
}

private fun details(s: Survey): String {
    val parts = mutableListOf(Day.pretty(s.date, "EEEE d MMMM yyyy"))
    if (s.time.isNotEmpty()) parts.add(s.time)
    if (s.difficulty >= 0) parts.add(SurveyText.current.difficultyOptions[s.difficulty])
    return parts.joinToString(" · ")
}

/** PHQ-9 and GAD-7 scores over time, one line each, on the same 0-27 points axis. */
@Composable
fun SurveyChart(surveys: List<Survey>, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ring = MaterialTheme.colorScheme.surfaceContainer
    val colours = mapOf("phq9" to Palette.health, "gad7" to Palette.anxiety)
    val first = Day.date(surveys.first().date)!!
    val days = maxOf(1L, Day.between(surveys.first().date, surveys.last().date))
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Column(Modifier.fillMaxHeight().padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                for (v in listOf(27, 18, 9, 0)) Text("$v", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 7.dp)) {
                fun y(score: Int) = size.height * (1f - score / 27f)
                fun x(day: String) = if (surveys.size < 2 || Day.between(surveys.first().date, surveys.last().date) == 0L)
                    size.width / 2 else size.width * Day.between(first.toString(), day) / days.toFloat()
                for (v in listOf(0, 9, 18, 27)) drawLine(grid, Offset(0f, y(v)), Offset(size.width, y(v)), 1f)
                for ((kind, colour) in colours) {
                    val points = surveys.filter { it.kind == kind }.map { Offset(x(it.date), y(it.score)) }
                    for (i in 1 until points.size) drawLine(colour, points[i - 1], points[i], 2.dp.toPx(), StrokeCap.Round)
                    for (p in points) {
                        drawCircle(ring, 5.5.dp.toPx(), p)
                        drawCircle(colour, 4.dp.toPx(), p)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            for (kind in SurveyKind.entries) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(colours.getValue(kind.raw)))
                    Text(kind.title, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** One questionnaire, with the official words. Save needs every question answered. */
@Composable
fun SurveyAnswerDialog(kind: SurveyKind, onCancel: () -> Unit, onSaved: (Survey) -> Unit) {
    val app = LocalApp.current
    val form = kind.text
    val texts = SurveyText.current
    val answers = remember { mutableStateListOf<Int?>().apply { repeat(kind.size) { add(null) } } }
    var difficulty by remember { mutableIntStateOf(-1) }
    var private by remember { mutableStateOf(app.privateDefault) }
    val left = answers.count { it == null }
    EditorDialog(kind.title, canSave = left == 0, onCancel = onCancel, onSave = {
        val values = answers.map { it ?: 0 }
        val survey = app.store.addSurvey(kind, values, if (values.any { it > 0 }) difficulty else -1,
            app.privateTick && private)
        onSaved(survey)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(form.about, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(form.question, style = MaterialTheme.typography.titleMedium)
            Text(if (left > 0) trn("%@ question left", "%@ questions left", left)
                 else tr("Score: %@ of %@", "${answers.sumOf { it ?: 0 }}", "${kind.maxScore}"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (i in 0 until kind.size) {
            Section(padded = true) {
                Text("${i + 1}. ${form.items[i]}", Modifier.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                for (v in 0..3) {
                    Line()
                    Choice(form.options[v], v, answers[i] == v) { answers[i] = v }
                }
            }
        }
        if (answers.any { (it ?: 0) > 0 }) {
            Section {
                Text(texts.difficulty, Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold)
                for (v in 0..3) {
                    Line()
                    Choice(texts.difficultyOptions[v], null, difficulty == v) { difficulty = v }
                }
            }
        }
        if (app.privateTick) Section {
            SwitchRow(tr("Private: keep it out of the doctor report"), private) { private = it }
        }
        Text(texts.credit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Choice(label: String, number: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (number != null) Text("$number", Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** After a PHQ-9 with question 9 above "Not at all": kind words and helplines. Moodlog never
 *  tells or alerts anyone. */
@Composable
fun SurveyCareDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Filled.Favorite, null, tint = Palette.mood(1).copy(alpha = 0.85f)) },
        title = { Text(tr("Thank you for answering honestly")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("You said you've had thoughts of being better off dead or of hurting yourself. That's a lot to carry, and you don't have to carry it alone. Please tell your doctor, or someone you trust, soon. If it feels heavy right now, you can talk to someone today:"))
                HelplineRows()
                Text(tr("Sottovoce doesn't tell anyone: this stays on your devices."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close")) } },
    )
}
