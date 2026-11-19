package io.github.akiig.moodlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.Mood
import io.github.akiig.moodlog.tr
import kotlin.math.max
import kotlin.math.min

/** The five line-drawn faces, the same drawing as on the PC and iPhone. */
@Composable
fun Face(mood: Int, modifier: Modifier = Modifier, ink: Color = MaterialTheme.colorScheme.onSurface,
         outline: Boolean = true) {
    Canvas(modifier.semantics { contentDescription = Mood.name(mood) }) {
        val r = min(size.width, size.height) / 2 - 1
        val cx = size.width / 2
        val cy = size.height / 2
        val line = Stroke(width = max(1.4f, r * 0.13f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (outline) drawCircle(ink, r, Offset(cx, cy), style = line)
        val ex = r * 0.36f
        val ey = r * 0.20f
        val er = max(1f, r * 0.11f)
        for (sx in listOf(-1f, 1f)) drawCircle(ink, er, Offset(cx + sx * ex, cy - ey))
        val w = r * 0.46f
        val y = cy + r * 0.30f
        val mouth = Path()
        when (mood) {
            1 -> {
                val brows = Path()
                for (sx in listOf(-1f, 1f)) {
                    brows.moveTo(cx + sx * (ex + r * 0.18f), cy - ey - r * 0.22f)
                    brows.lineTo(cx + sx * (ex - r * 0.14f), cy - ey - r * 0.36f)
                }
                drawPath(brows, ink, style = line)
                mouth.moveTo(cx - w, y + r * 0.22f)
                mouth.cubicTo(cx - w * 0.5f, y - r * 0.26f, cx + w * 0.5f, y - r * 0.26f, cx + w, y + r * 0.22f)
                drawPath(mouth, ink, style = line)
            }
            2 -> {
                mouth.moveTo(cx - w, y + r * 0.12f)
                mouth.cubicTo(cx - w * 0.5f, y - r * 0.12f, cx + w * 0.5f, y - r * 0.12f, cx + w, y + r * 0.12f)
                drawPath(mouth, ink, style = line)
            }
            3 -> {
                mouth.moveTo(cx - w, y + r * 0.04f)
                mouth.lineTo(cx + w, y + r * 0.04f)
                drawPath(mouth, ink, style = line)
            }
            4 -> {
                mouth.moveTo(cx - w, y - r * 0.08f)
                mouth.cubicTo(cx - w * 0.5f, y + r * 0.24f, cx + w * 0.5f, y + r * 0.24f, cx + w, y - r * 0.08f)
                drawPath(mouth, ink, style = line)
            }
            else -> {
                mouth.moveTo(cx - w * 1.1f, y - r * 0.14f)
                mouth.lineTo(cx + w * 1.1f, y - r * 0.14f)
                mouth.cubicTo(cx + w * 0.8f, y + r * 0.44f, cx - w * 0.8f, y + r * 0.44f, cx - w * 1.1f, y - r * 0.14f)
                mouth.close()
                drawPath(mouth, ink)
                drawPath(mouth, ink, style = line)
            }
        }
    }
}

/** A filled circle in the mood colour with the face on it. */
@Composable
fun MoodBadge(mood: Int, size: Dp = 28.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Palette.mood(mood)), contentAlignment = Alignment.Center) {
        Face(mood, Modifier.fillMaxSize().padding(size * 0.12f), Palette.faceInk(mood), outline = false)
    }
}

@Composable
fun MoodPicker(value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (n in 1..5) {
            val chosen = value == n
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(if (chosen) Palette.mood(n).copy(alpha = 0.45f) else Color.Transparent)
                    .clickable { onChange(if (chosen) 0 else n) }
                    .semantics { selected = chosen }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Face(n, Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text(Mood.name(n), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** Five buttons 1-5; tapping the chosen one again clears it (the scales are optional). */
@Composable
fun ScalePicker(title: String, words: Map<Int, String>, color: Color, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(tr(title), Modifier.weight(1f))
            Text(if (value > 0) "$value · ${words[value] ?: ""}" else tr("not set"),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (n in 1..5) {
                val chosen = value == n
                Box(
                    Modifier.weight(1f).clip(CircleShape)
                        .background(if (chosen) color else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onChange(if (chosen) 0 else n) }
                        .semantics { contentDescription = "${tr(title)} $n, ${words[n] ?: ""}"; selected = chosen }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$n", fontWeight = FontWeight.SemiBold,
                        color = if (chosen) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
