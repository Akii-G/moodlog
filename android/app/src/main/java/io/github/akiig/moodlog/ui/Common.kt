package io.github.akiig.moodlog.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.akiig.moodlog.Day
import io.github.akiig.moodlog.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// iPhone-style pieces: large titles, grouped lists of white cards on a grey page, blue text
// buttons, green switches and segmented controls. Built from plain Compose so they look
// the same on every Android phone.

/** Space left at the bottom of each screen for the floating tab bar. */
val TabBarSpace = 110.dp

/** The big bold title at the top of a tab, with round buttons on the right. */
@Composable
fun LargeTitle(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically, content = trailing)
        Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 2,
            modifier = Modifier.padding(bottom = 8.dp))
    }
}

/** A "‹ Log" back button, then the big title. */
@Composable
fun BackTitle(back: String, title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack)
            .padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Text(back, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        }
        Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 2,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
    }
}

/** A round button with an icon, like the iPhone's + at the top right. */
@Composable
fun CircleButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f))
        .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(onClick = onClick).semantics { role = Role.Button },
        contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** A group of rows on a white card, with an optional grey title above and note below. */
@Composable
fun Section(title: String? = null, footer: String? = null, padded: Boolean = true,
            content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title.uppercase(io.github.akiig.moodlog.appLocale()), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (padded) Modifier.padding(horizontal = 16.dp, vertical = 4.dp) else Modifier), content = content)
        if (footer != null) {
            Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

/** The thin line between two rows of a section. */
@Composable
fun Line() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun RowBase(onClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** A label on the left and a value on the right; tappable when onClick is given. */
@Composable
fun ValueRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    RowBase(onClick) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
    }
}

@Composable
fun LinkRow(label: String, value: String? = null, onClick: () -> Unit) {
    RowBase(onClick) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    RowBase({ onChange(!value) }) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
        IosSwitch(value, onChange)
    }
}

/** A blue text button row, with a spinner while busy. */
@Composable
fun ActionRow(label: String, enabled: Boolean = true, busy: Boolean = false, destructive: Boolean = false,
              onClick: () -> Unit) {
    RowBase(if (enabled && !busy) onClick else null) {
        val color = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            destructive -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
        Text(label, Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodyLarge)
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/** A text field inside a section: a small grey label above what you type. */
@Composable
fun FieldRow(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "",
             secret: Boolean = false, keyboard: KeyboardOptions = KeyboardOptions.Default, minLines: Int = 1,
             trailing: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value, onChange, Modifier.weight(1f).padding(vertical = 4.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = minLines == 1, minLines = minLines, keyboardOptions = keyboard,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        inner()
                    }
                })
            trailing?.invoke()
        }
    }
}

/** iOS's switch: a green pill with a white knob that slides. */
@Composable
fun IosSwitch(value: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (value) Palette.on else MaterialTheme.colorScheme.surfaceContainerHighest, label = "track")
    val x by animateDpAsState(if (value) 20.dp else 0.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "knob")
    Box(Modifier.width(51.dp).height(31.dp).clip(CircleShape).background(track)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!value) }
        .semantics { role = Role.Switch; stateDescription = if (value) tr("On") else tr("Off") }
        .padding(2.dp)) {
        Box(Modifier.offset(x = x).size(27.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

/** iOS's segmented control: a grey track with the chosen option on a white pill. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(2.dp)) {
        for ((key, label) in options) {
            val on = key == selected
            Box(Modifier.weight(1f).heightIn(min = 34.dp)
                .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(8.dp)) else Modifier)
                .clip(RoundedCornerShape(8.dp))
                .background(if (!on) Color.Transparent else if (isSystemInDarkTheme()) Color(0xff636366) else Color.White)
                .clickable { onSelect(key) }
                .semantics { this.selected = on; role = Role.Tab }
                .padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A full-screen editor like the iPhone's sheets: Cancel on the left, Save on the right. */
@Composable
fun EditorDialog(title: String, canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit,
                 content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp)) {
                    TextButton(onClick = onCancel, Modifier.align(Alignment.CenterStart)) {
                        Text(tr("Cancel"), style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(title, Modifier.align(Alignment.Center).padding(horizontal = 96.dp),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onSave, enabled = canSave, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(tr("Save"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    content()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(day: String, allowFuture: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val start = (Day.date(day) ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val todayEnd = LocalDate.now().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = start,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allowFuture || utcTimeMillis < todayEnd
        })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                onDismiss()
            }) { Text(tr("OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    ) { DatePicker(state, showModeToggle = false) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(time: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(tr("OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
        text = { TimePicker(state) },
    )
}

@Composable
fun EmptyText(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}
