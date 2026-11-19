package io.github.akiig.moodlog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.akiig.moodlog.tr
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

/** The welcome tour: shown once on a new install, and again from Settings. Each step can
 *  switch to a tab, so the person sees each screen while it is explained. */
class TourState {
    var step by mutableStateOf<Int?>(null)
    var tabBar by mutableStateOf(Rect.Zero)
    val frames = mutableStateMapOf<String, Rect>()

    fun start() {
        step = 0
    }
}

/** Lets the tour put a spotlight on this view. */
fun Modifier.tourTarget(name: String, tour: TourState) =
    onGloballyPositioned { tour.frames[name] = it.boundsInRoot() }

data class TourStep(val tab: Int?, val target: String?, val pointsAtTabs: Boolean,
                    val title: String, val text: String)

object Tour {
    val steps: List<TourStep>
        get() = listOf(
            TourStep(0, null, false, tr("Welcome to Moodlog"),
                tr("Hey! No pressure, I'll help you learn how the interface works. Don't get overwhelmed, it's quite simple, I'll take care of you.")),
            TourStep(0, null, true, tr("Five tabs, down here"),
                tr("Down here are the tabs. You'll spend most of your time in Log; the others are there whenever you want them. Let's have a quick look at each.")),
            TourStep(0, "calendar", false, tr("Your month at a glance"),
                tr("Each day takes the colour of your mood. Tap a day to see it or to log how you feel: pick a face, that's the whole job. Energy, anxiety and a note are optional.")),
            TourStep(1, null, true, tr("Charts fill in on their own"),
                tr("Your days turn into charts. Nothing to set up: the more you log, the more they show.")),
            TourStep(2, null, true, tr("Dreams, if you remember them"),
                tr("A small dream journal, for the mornings you remember something. Totally optional.")),
            TourStep(3, null, true, tr("Mark what matters"),
                tr("Started a new medication, or something big happened? Add it here, and Moodlog shows how you felt in the two weeks before and after.")),
            TourStep(4, null, true, tr("Your PC and Health Connect"),
                tr("Here you can connect to Moodlog on your PC and send sleep and steps from Health Connect. All optional: the app works on its own too. You can watch this tour again from here.")),
            TourStep(0, null, false, tr("That's all, really"),
                tr("Everything stays on your phone, private. Log a mood whenever you feel like it; even once a day is plenty. You've got this.")),
        )
}

@Composable
fun TourOverlay(tour: TourState, onTab: (Int) -> Unit, onFinish: () -> Unit) {
    val steps = Tour.steps
    val index = (tour.step ?: 0).coerceIn(0, steps.size - 1)
    val step = steps[index]
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(index) { step.tab?.let(onTab) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val bar = tour.tabBar
        val barTop = if (bar.height > 0) bar.top else h - with(density) { 80.dp.toPx() }
        val hole = step.target?.let { tour.frames[it] }?.inflate(with(density) { 6.dp.toPx() }) ?: Rect.Zero
        val showScreen = step.pointsAtTabs && step.target == null && index > 1
        val dim by animateFloatAsState(if (showScreen) 0.18f else 0.6f, label = "dim")
        val tabX = ((step.tab ?: 0) + 0.5f) * w / 5
        var cardHeight by remember { mutableStateOf(0) }
        val centred = step.target == null && !step.pointsAtTabs
        val margin = with(density) { 16.dp.toPx() }
        val cardTop = when {
            centred -> (h - cardHeight) / 2
            hole != Rect.Zero && hole.bottom + cardHeight + 2 * margin < barTop -> hole.bottom + margin
            else -> barTop - cardHeight - with(density) { 90.dp.toPx() }
        }.coerceAtLeast(margin)
        val animatedTop by animateFloatAsState(cardTop, spring(dampingRatio = 0.86f, stiffness = 300f), label = "top")

        // the dimmed screen with a hole; the tab bar stays lit. Taps stay on the tour.
        Canvas(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) {}) {
            val p = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, barTop))
                if (hole.width > 1) addRoundRect(androidx.compose.ui.geometry.RoundRect(hole, CornerRadius(16.dp.toPx())))
            }
            drawPath(p, Color.Black.copy(alpha = dim))
        }
        if (hole != Rect.Zero) PulsingRing(hole, accent)
        if (step.pointsAtTabs && cardHeight > 0) {
            PointerLine(Offset(tabX.coerceIn(40f, w - 40f), animatedTop + cardHeight), Offset(tabX, barTop + 6f), accent, index)
        }

        AnimatedContent(index, Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, animatedTop.roundToInt()) }
            .padding(horizontal = 16.dp),
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            contentAlignment = Alignment.TopCenter, label = "card",
        ) { i ->
            val s = steps[i]
            Surface(
                Modifier.widthIn(max = 380.dp).onSizeChanged { cardHeight = it.height },
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 6.dp, shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.target == null && !s.pointsAtTabs) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TourFace() }
                    }
                    Text(s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(s.text, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (d in steps.indices) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(
                                if (d == i) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (i < steps.size - 1) TextButton(onClick = onFinish) { Text(tr("Skip tour")) }
                        Spacer(Modifier.weight(1f))
                        if (i > 0) {
                            OutlinedButton(onClick = { tour.step = i - 1 }) { Text(tr("Back")) }
                            Spacer(Modifier.size(8.dp))
                        }
                        Button(onClick = { if (i == steps.size - 1) onFinish() else tour.step = i + 1 }) {
                            Text(if (i == steps.size - 1) tr("Let's start") else tr("Next"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingRing(rect: Rect, color: Color) {
    val t = rememberInfiniteTransition(label = "ring")
    val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(950), RepeatMode.Reverse), label = "alpha")
    Canvas(Modifier.fillMaxSize()) {
        drawRoundRect(color.copy(alpha = a), rect.topLeft, rect.size, CornerRadius(16.dp.toPx()),
            style = Stroke(3.dp.toPx()))
    }
}

/** A curved line with a dot at its end, from the card down to a tab. */
@Composable
private fun PointerLine(from: Offset, to: Offset, color: Color, key: Int) {
    val drawn = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(200)
        drawn.animateTo(1f, tween(450))
    }
    Canvas(Modifier.fillMaxSize()) {
        val mid = (from.y + to.y) / 2
        val path = Path().apply {
            moveTo(from.x, from.y)
            cubicTo(from.x, mid, to.x, mid, to.x, to.y)
        }
        val m = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        m.getSegment(0f, m.length * drawn.value, part, true)
        drawPath(part, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        if (drawn.value > 0.95f) drawCircle(color, 4.5.dp.toPx(), to)
    }
}

/** A face that pops in, bobs gently and smiles more and more. */
@Composable
private fun TourFace() {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            t = (System.nanoTime() - start) / 1e9f
            delay(16)
        }
    }
    val mood = if (t < 0.6f) 3 else if (t < 1.3f) 4 else 5
    Box(
        Modifier.size(76.dp)
            .offset { IntOffset(0, (sin(t * 2.4f) * 3 * density).roundToInt()) }
            .scale(0.85f + 0.15f * (t / 0.5f).coerceAtMost(1f))
            .clip(CircleShape).background(Palette.mood(mood)),
        contentAlignment = Alignment.Center,
    ) {
        Face(mood, Modifier.fillMaxSize().padding(8.dp), Palette.faceInk(mood), outline = false)
    }
}
