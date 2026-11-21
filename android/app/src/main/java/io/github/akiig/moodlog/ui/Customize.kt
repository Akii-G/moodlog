package io.github.akiig.moodlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.akiig.moodlog.LocalApp
import io.github.akiig.moodlog.tr

/** "Customize your experience": parts of Moodlog you can turn off. Nothing is deleted. */
@Composable
fun CustomizeOptions() {
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth().clickable { app.chooseDreams(!app.showDreams) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(tr("Dreams"), style = MaterialTheme.typography.bodyLarge)
            Text(tr("The Dreams tab and the moons on the calendar. Your dreams are kept even when it's off."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IosSwitch(app.showDreams) { app.chooseDreams(it) }
    }
    Line()
    OptionRow(tr("Feeling words"), tr("Optional words next to a mood, like Joy or Tiredness. Kept even when it's off."),
        app.showFeelings) { app.chooseFeelings(it) }
    Line()
    OptionRow(tr("Writing prompts"), tr("A gentle question in an empty note, for days you don't know what to write."),
        app.showPrompts) { app.choosePrompts(it) }
    Line()
    OptionRow(tr("Questionnaires"), tr("PHQ-9 and GAD-7 for your doctor, with a gentle reminder every two weeks."),
        app.showQuestionnaires) { app.chooseQuestionnaires(it) }
    Line()
    OptionRow(tr("Private notes and dreams"),
        tr("A Private tick on each note and dream: private notes never go in the doctor report, and private dreams are only counted."),
        app.privateTick) { app.choosePrivateTick(it) }
    Line()
    OptionRow(tr("New ones start private"), tr("Every new note and dream is ticked private. You can still untick it."),
        app.privateDefault, enabled = app.privateTick) { app.choosePrivateDefault(it) }
    Line()
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Days with several moods"), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((mode, label) in listOf("grid" to tr("All of them, in a grid"), "latest" to tr("Only the latest"))) {
                val on = app.several == mode
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.weight(1f).clip(shape)
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(2.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                    .clickable { app.chooseSeveral(mode) }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeveralPreview(mode)
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, hint: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
        .then(if (enabled) Modifier.clickable { onChange(!value) } else Modifier).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IosSwitch(value) { if (enabled) onChange(it) }
    }
}

/** A tiny calendar day with three moods: as a grid, or only the latest. */
@Composable
private fun SeveralPreview(mode: String) {
    val shape = RoundedCornerShape(9.dp)
    Box(Modifier.size(40.dp).clip(shape)) {
        if (mode == "latest") {
            Box(Modifier.fillMaxSize().background(Palette.mood(4)))
            Face(4, Modifier.fillMaxSize().padding(9.dp), Palette.faceInk(4), outline = false)
        } else {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                .padding(top = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Palette.mood(2)))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Palette.mood(3)))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Palette.mood(4)))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Shown once after updating; the same options are in Settings. */
@Composable
fun CustomizeDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Customize your experience"), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(tr("Turn off what you don't need. Nothing is deleted, and you can change this any time in Settings."),
                    style = MaterialTheme.typography.bodyMedium)
                Section { CustomizeOptions() }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Text(tr("Done"), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
