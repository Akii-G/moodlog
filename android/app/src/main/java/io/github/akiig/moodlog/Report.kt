package io.github.akiig.moodlog

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * The report for your doctor, the same sections as Moodlog on the PC: a summary, how often
 * each mood was logged, mood / energy and anxiety / sleep over the period, events with the
 * mood before and after, dreams, and a day-by-day table. A4, printable, in the app's language.
 */
class Report(
    private val store: AppStore,
    private val start: String,
    private val end: String,
    private val includeNotes: Boolean,
    private val sleep: Map<String, Double>,      // empty when not included
    private val includeDreams: Boolean,
    private val dreamText: Boolean,
    private val includeSurveys: Boolean = false,
) {
    companion object {
        const val W = 595f
        const val H = 842f
        const val M = 42f
        val ink = Color.parseColor("#1b1b1b")
        val ink2 = Color.parseColor("#4a4945")
        val muted = Color.parseColor("#6d6b66")
        val grid = Color.parseColor("#e3e2dc")
        val tile = Color.parseColor("#f4f3ef")
        val health = Color.parseColor("#1baf7a")
        val energy = Color.parseColor("#b87c10")
        val anxiety = Color.parseColor("#7b5ad6")
        val moods = mapOf(1 to "#cf3f3f", 2 to "#eb9a94", 3 to "#bdbbb3", 4 to "#8fbcf0", 5 to "#2a78d6")
            .mapValues { Color.parseColor(it.value) }
    }

    /** Where pages go: a PDF normally, images in the screenshot test. */
    interface Pages {
        fun start(number: Int): Canvas
        fun end()
    }

    private var pages: Pages? = null
    private var open = false
    private lateinit var c: Canvas
    private var pageNo = 0
    private var y = M

    private val days: List<String> = run {
        val out = mutableListOf<String>()
        var d = start
        while (d <= end) { out.add(d); d = Day.adding(1, d) }
        out
    }

    // ------------------------------------------------------------------ drawing helpers

    private fun paint(size: Float, color: Int = ink, bold: Boolean = false, italic: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF,
                if (bold && italic) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD else if (italic) Typeface.ITALIC else Typeface.NORMAL)
        }

    /** Draws wrapped text at (x, top); returns its height. */
    private fun text(s: String, x: Float, top: Float, size: Float = 9f, color: Int = ink, bold: Boolean = false,
                     width: Float = W - M - x, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
                     italic: Boolean = false, draw: Boolean = true): Float {
        val p = paint(size, color, bold, italic)
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(1))
            .setAlignment(align).setLineSpacing(0f, 1.15f).build()
        if (draw) {
            c.save(); c.translate(x, top); layout.draw(c); c.restore()
        }
        return layout.height.toFloat()
    }

    private fun measure(s: String, size: Float, width: Float, bold: Boolean = false): Float {
        val p = paint(size, ink, bold)
        return StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(1))
            .setLineSpacing(0f, 1.15f).build().height.toFloat()
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun newPage() {
        if (open) { footer(); pages!!.end() }
        pageNo++
        c = pages!!.start(pageNo)
        c.drawColor(Color.WHITE)
        open = true
        y = M
    }

    private fun footer() {
        text(tr("Made with Moodlog · self-reported data, not a medical record"), M, H - 28f, 7.5f, muted)
        text(tr("Page %@", pageNo), M, H - 28f, 7.5f, muted, align = Layout.Alignment.ALIGN_OPPOSITE, width = W - 2 * M)
    }

    private fun ensure(height: Float) {
        if (y + height > H - 48f) newPage()
    }

    private fun heading(s: String) {
        ensure(40f)
        y += 8f
        y += text(s, M, y, 12f, ink, bold = true) + 6f
    }

    // ------------------------------------------------------------------ the report

    /** Writes the PDF. */
    fun write(file: File): File {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        draw(object : Pages {
            override fun start(number: Int): Canvas {
                page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), number).create())
                return page!!.canvas
            }
            override fun end() { doc.finishPage(page) }
        })
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Draws every page; returns how many. */
    fun draw(target: Pages): Int {
        pages = target
        newPage()
        val moodDays = days.filter { store.average(it) != null }
        val entries = days.flatMap { store.entriesOn(it) }

        y += text(tr("Mood report"), M, y, 20f, ink, bold = true) + 2f
        y += text(tr("%@ – %@ · %@ days · made with Moodlog on %@", Day.pretty(start, "d MMM yyyy"),
            Day.pretty(end, "d MMM yyyy"), days.size, Day.pretty(Day.today, "d MMM yyyy")), M, y, 9f, muted) + 14f

        // summary tiles
        val tiles = mutableListOf(
            tr("Days with a mood") to tr("%@ of %@", moodDays.size, days.size),
            tr("Average mood (1–5)") to (moodDays.mapNotNull { store.average(it) }.takeIf { it.isNotEmpty() }
                ?.average()?.let { "${one(it)}  ${Mood.name(Mood.level(it))}" } ?: "–"),
            tr("Moods logged") to "${entries.count { it.mood > 0 }}",
        )
        val en = entries.map { it.energy }.filter { it > 0 }
        val an = entries.map { it.anxiety }.filter { it > 0 }
        if (en.isNotEmpty()) tiles.add(tr("Average energy (1–5)") to one(en.average()))
        if (an.isNotEmpty()) tiles.add(tr("Average anxiety (1–5)") to one(an.average()))
        if (sleep.isNotEmpty()) tiles.add(tr("Average sleep") to tr("%@ h (%@ nights)", one(sleep.values.average()), sleep.size))
        val tw = (W - 2 * M - 2 * 8f) / 3
        tiles.chunked(3).forEach { row ->
            row.forEachIndexed { i, (label, value) ->
                val x = M + i * (tw + 8f)
                c.drawRoundRect(RectF(x, y, x + tw, y + 46f), 6f, 6f, fill(tile))
                text(label, x + 8f, y + 6f, 7.5f, muted, width = tw - 16f)
                text(value, x + 8f, y + 20f, 13f, ink, bold = true, width = tw - 16f)
            }
            y += 54f
        }

        if (entries.any { it.mood > 0 }) countBars(entries)
        if (moodDays.isNotEmpty()) moodChart()
        if (en.isNotEmpty() || an.isNotEmpty()) scaleChart()
        if (sleep.isNotEmpty()) sleepChart()
        eventsTable()
        if (includeSurveys) surveyTable()
        if (includeDreams) dreamsSection()
        dayTable()

        footer(); target.end(); open = false
        return pageNo
    }

    private fun countBars(entries: List<Entry>) {
        ensure(110f)
        y += text(tr("How often each mood was logged"), M, y, 10f, ink, bold = true) + 6f
        val counts = (1..5).associateWith { l -> entries.count { it.mood == l } }
        val most = counts.values.max().coerceAtLeast(1)
        val labelW = 70f
        val barW = W - 2 * M - labelW - 40f
        for (level in 5 downTo 1) {
            val n = counts[level]!!
            text(Mood.name(level), M, y + 1f, 8.5f, ink2, width = labelW - 6f)
            c.drawRoundRect(RectF(M + labelW, y + 2f, M + labelW + barW * n / most, y + 12f), 2f, 2f, fill(moods[level]!!))
            text("$n", M + labelW + barW * n / most + 5f, y + 1f, 8.5f, muted, width = 40f)
            y += 15f
        }
        y += 8f
    }

    private val weekly get() = days.size > 92

    /** Day (or week) averages as (index, value) pairs, with the number of slots. */
    private fun series(value: (String) -> Double?): Pair<List<Pair<Int, Double>>, Int> {
        if (!weekly) return days.mapIndexedNotNull { i, d -> value(d)?.let { i to it } } to days.size
        val weeks = days.chunked(7)
        return weeks.mapIndexedNotNull { i, w -> w.mapNotNull(value).takeIf { it.isNotEmpty() }?.average()?.let { i to it } } to weeks.size
    }

    private fun frame(title: String, height: Float, labels: List<String>, note: String? = null,
                      legend: List<Pair<String, Int>> = emptyList(), min: Double = 1.0, max: Double = 5.0):
            Triple<Float, Float, (Double) -> Float> {
        ensure(height + 60f)
        y += text(title, M, y, 10f, ink, bold = true) + 4f
        if (legend.isNotEmpty()) {
            var x = M
            for ((name, color) in legend) {
                c.drawCircle(x + 4f, y + 6f, 3.5f, fill(color))
                x += 12f
                text(name, x, y, 8f, ink2, width = 120f)
                x += paint(8f).measureText(name) + 16f
            }
            y += 14f
        }
        val left = M + 52f
        val top = y + 4f
        val yOf = { v: Double -> (top + height - (v - min) / (max - min) * height).toFloat() }
        labels.forEachIndexed { i, l ->
            val v = min + i * (max - min) / (labels.size - 1)
            c.drawLine(left, yOf(v), W - M, yOf(v), stroke(grid, 0.6f))
            text(l, M, yOf(v) - 5f, 7.5f, muted, width = 48f)
        }
        y = top + height + 4f
        text(if (weekly) tr("weeks") else Day.pretty(start, "d MMM"), left, y, 7f, muted, width = 120f)
        text(Day.pretty(end, "d MMM"), left, y, 7f, muted, width = W - M - left, align = Layout.Alignment.ALIGN_OPPOSITE)
        y += 12f
        if (note != null) y += text(note, M, y, 7.5f, muted, italic = true) + 4f
        y += 8f
        return Triple(left, top, yOf)
    }

    private fun xOf(i: Int, n: Int, left: Float) = if (n <= 1) (left + W - M) / 2 else left + i * (W - M - left) / (n - 1)

    private fun moodChart() {
        val (points, n) = series { store.average(it) }
        val evs = store.events.filter { it.date in start..end }.sortedBy { it.date }
        val (left, top, yOf) = frame(tr("Mood, %@", if (weekly) tr("weekly average") else tr("daily average")), 118f,
            (1..5).map { Mood.name(it) }, if (evs.isNotEmpty()) tr("Numbered marks are the events listed below.") else null)
        evs.forEachIndexed { k, e ->
            val i = days.indexOf(e.date).let { if (weekly) it / 7 else it }
            val x = xOf(i, n, left)
            c.drawLine(x, top, x, yOf(1.0), stroke(muted, 0.7f).apply { pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f) })
            text("${k + 1}", x - 10f, top - 13f, 7f, ink2, bold = true, width = 20f, align = Layout.Alignment.ALIGN_CENTER)
        }
        drawLine(points, n, left, yOf, Color.parseColor("#9a9890")) { v -> moods[Mood.level(v)]!! }
    }

    private fun drawLine(points: List<Pair<Int, Double>>, n: Int, left: Float, yOf: (Double) -> Float, line: Int,
                         dot: (Double) -> Int) {
        val path = Path()
        points.forEachIndexed { k, (i, v) ->
            val x = xOf(i, n, left)
            if (k == 0) path.moveTo(x, yOf(v)) else path.lineTo(x, yOf(v))
        }
        c.drawPath(path, stroke(line, 1.2f))
        for ((i, v) in points) {
            c.drawCircle(xOf(i, n, left), yOf(v), 3.2f, fill(Color.WHITE))
            c.drawCircle(xOf(i, n, left), yOf(v), 2.4f, fill(dot(v)))
        }
    }

    private fun scaleChart() {
        val (e, n) = series { store.scaleAverage(it, true) }
        val (a, _) = series { store.scaleAverage(it, false) }
        val (left, _, yOf) = frame(tr("Energy and anxiety (1–5)"), 96f, (1..5).map { "$it" },
            tr("Energy: 1 very low, 5 very high. Anxiety: 1 calm, 5 very anxious."),
            legend = listOf(tr("Energy") to energy, tr("Anxiety") to anxiety))
        drawLine(e, n, left, yOf, energy) { energy }
        drawLine(a, n, left, yOf, anxiety) { anxiety }
    }

    private fun sleepChart() {
        val (points, n) = series { sleep[it] }
        val top = (points.maxOfOrNull { it.second } ?: 8.0).coerceAtLeast(9.0).let { Math.ceil(it / 3) * 3 }
        val labels = (0..3).map { "${(it * top / 3).toInt()} h" }
        val (left, _, yOf) = frame(tr("Sleep (hours a night%@)", if (weekly) tr(", weekly average") else ""), 90f,
            labels, min = 0.0, max = top)
        val bw = ((W - M - left) / n.coerceAtLeast(1) * 0.7f).coerceIn(1.5f, 10f)
        for ((i, v) in points) {
            val x = xOf(i, n, left)
            c.drawRect(x - bw / 2, yOf(v), x + bw / 2, yOf(0.0), fill(health))
        }
    }

    private fun eventsTable() {
        val evs = store.events.filter { it.date in start..end }.sortedBy { it.date }
        if (evs.isEmpty()) return
        heading(tr("Events"))
        val cols = listOf(18f, 66f, 74f, 0f, 70f, 70f)
        val whatW = W - 2 * M - cols.sum()
        val widths = cols.toMutableList().also { it[3] = whatW }
        val heads = listOf("#", tr("Date"), tr("Type"), tr("What happened"), tr("Mood 2w before"), tr("Mood 2w after"))
        fun head() {
            var x = M
            heads.forEachIndexed { i, h -> text(h, x, y, 7.5f, muted, bold = true, width = widths[i] - 4f); x += widths[i] }
            y += 14f
            c.drawLine(M, y - 3f, W - M, y - 3f, stroke(grid, 0.6f))
        }
        head()
        evs.forEachIndexed { k, e ->
            val around = store.moodAround(e.date)
            fun w(win: AppStore.Window) = if (win.days < 3) "–" else "${one(win.average)} (${win.days})"
            val what = if (e.note.isNotEmpty()) "${e.title}\n${e.note}" else e.title
            val cells = listOf("${k + 1}", Day.pretty(e.date, "d MMM yyyy"), EventKind.label(e.kind), what,
                w(around.before), w(around.after) + if (!around.complete && around.after.days >= 3) " *" else "")
            val rowH = cells.indices.maxOf { measure(cells[it], 8.5f, widths[it] - 4f) }.coerceAtLeast(12f) + 6f
            if (y + rowH > H - 48f) { newPage(); head() }
            var x = M
            cells.forEachIndexed { i, s -> text(s, x, y, 8.5f, if (i == 0) muted else ink, width = widths[i] - 4f); x += widths[i] }
            y += rowH
        }
        if (evs.any { !store.moodAround(it.date).complete }) y += text("* " + tr("so far"), M, y, 7f, muted, italic = true)
        y += 6f
    }

    /** PHQ-9 and GAD-7: date, score, each answer in question order, difficulty. Private ones are left out. */
    private fun surveyTable() {
        val list = store.surveys.filter { it.date in start..end && !it.private }
        if (list.isEmpty()) return
        heading(tr("Questionnaires"))
        y += text(tr("Answered in Moodlog. Each answer counts 0 (not at all) to 3 (nearly every day); the score is their sum, out of 27 for PHQ-9 and 21 for GAD-7. The answers are listed in question order. Moodlog gives no interpretation."),
            M, y, 8f, muted, width = W - 2 * M) + 6f
        val widths = mutableListOf(70f, 64f, 56f, 120f, 0f).also { it[4] = W - 2 * M - it.sum() }
        val heads = listOf(tr("Date"), tr("Questionnaire"), tr("Score"), tr("Answers"), tr("Difficulty"))
        fun head() {
            var x = M
            heads.forEachIndexed { i, h -> text(h, x, y, 7.5f, muted, bold = true, width = widths[i] - 4f); x += widths[i] }
            y += 14f
            c.drawLine(M, y - 3f, W - M, y - 3f, stroke(grid, 0.6f))
        }
        head()
        val words = SurveyText.current.difficultyOptions
        for (s in list) {
            val diff = if (s.difficulty >= 0) words[s.difficulty] else "–"
            val cells = listOf(Day.pretty(s.date, "d MMM yyyy"), s.name, "${s.score} / ${s.maxScore}",
                s.answers.joinToString(" "), diff)
            val rowH = measure(diff, 8.5f, widths[4] - 4f).coerceAtLeast(12f) + 6f
            if (y + rowH > H - 48f) { newPage(); head() }
            var x = M
            cells.forEachIndexed { i, t -> text(t, x, y, 8.5f, ink, bold = i == 2, width = widths[i] - 4f); x += widths[i] }
            y += rowH
        }
        val credit = SurveyText.current.credit
        ensure(measure(credit, 7f, W - 2 * M) + 4f)
        y += text(credit, M, y + 2f, 7f, muted, width = W - 2 * M, italic = true) + 10f
    }

    private fun dreamsSection() {
        val ds = store.dreams.filter { it.date in start..end }.sortedBy { it.date }
        if (ds.isEmpty()) return
        heading(tr("Dreams"))
        store.dreamSummary(start, end)?.let { y += text(it, M, y, 9f, ink2) + 6f }
        if (!dreamText) return
        for (d in ds) {
            val line = listOf(Day.pretty(d.date, "d MMM yyyy"), d.details).filter { it.isNotEmpty() }.joinToString(" · ")
            val h = measure(line, 8f, W - 2 * M, true) + (if (d.text.isNotEmpty() && !d.private) measure(d.text, 8.5f, W - 2 * M) else 0f) + 6f
            ensure(h)
            y += text(line, M, y, 8f, muted, bold = true)
            if (d.text.isNotEmpty() && !d.private) y += text(d.text, M, y, 8.5f, ink)   // private: counted only
            y += 6f
        }
    }

    private fun dayTable() {
        heading(tr("Day by day"))
        val names = mutableListOf(tr("Date") to 70f, tr("Mood") to 96f, tr("Energy") to 40f, tr("Anxiety") to 44f)
        if (sleep.isNotEmpty()) names.add(tr("Sleep") to 38f)
        names.add(tr("Event") to 34f)
        if (includeDreams) names.add(tr("Dream") to 40f)
        val notesW = W - 2 * M - names.sumOf { it.second.toDouble() }.toFloat()
        if (includeNotes) names.add(tr("Notes") to notesW)
        fun head() {
            var x = M
            for ((n, w) in names) { text(n, x, y, 7.5f, muted, bold = true, width = w - 4f); x += w }
            y += 14f
            c.drawLine(M, y - 3f, W - M, y - 3f, stroke(grid, 0.6f))
        }
        head()
        for (d in days.reversed()) {
            val es = store.entriesOn(d)
            val hasDream = includeDreams && store.dreamsOn(d).isNotEmpty()
            if (es.isEmpty() && store.eventsOn(d).isEmpty() && !hasDream && sleep[d] == null) continue
            val moodText = es.filter { it.mood > 0 }.joinToString(", ") { Mood.name(it.mood) }.ifEmpty { "–" }
            val notes = if (includeNotes) es.filter { !it.private }.mapNotNull { e -> e.note.takeIf { it.isNotEmpty() }?.let { (if (e.time.isNotEmpty()) e.time + " " else "") + it } }
                .joinToString("\n") else ""
            val cells = mutableListOf(Day.pretty(d, "EEE d MMM"), moodText,
                es.map { it.energy }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.let { one(it) } ?: "",
                es.map { it.anxiety }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.let { one(it) } ?: "")
            if (sleep.isNotEmpty()) cells.add(sleep[d]?.let { one(it) } ?: "")
            cells.add(if (store.eventsOn(d).isNotEmpty()) "●" else "")
            if (includeDreams) cells.add(if (hasDream) (if (store.dreamsOn(d).any { it.isNightmare }) tr("bad") else "●") else "")
            if (includeNotes) cells.add(notes)
            val rowH = names.indices.maxOf { i -> measure(cells[i], 8f, names[i].second - 4f) }.coerceAtLeast(10f) + 4f
            if (y + rowH > H - 48f) { newPage(); head() }
            var x = M
            names.forEachIndexed { i, (_, w) ->
                if (i == 1 && es.isNotEmpty()) {
                    val avg = store.average(d)
                    if (avg != null) c.drawCircle(x + 3f, y + 5f, 2.6f, fill(moods[Mood.level(avg)]!!))
                    text(cells[i], x + 9f, y, 8f, ink, width = w - 13f)
                } else text(cells[i], x, y, 8f, if (i == 0) ink2 else ink, width = w - 4f)
                x += w
            }
            y += rowH
            c.drawLine(M, y - 2f, W - M, y - 2f, stroke(grid, 0.4f))
        }
    }
}

/** The report's period choices, like the PC's. */
enum class ReportPeriod(val days: Int?) {
    MONTH(30), THREE_MONTHS(91), SIX_MONTHS(182), YEAR(null), ALL(null);

    val label: String
        get() = when (this) {
            MONTH -> tr("Last 30 days")
            THREE_MONTHS -> tr("Last 3 months")
            SIX_MONTHS -> tr("Last 6 months")
            YEAR -> tr("This year")
            ALL -> tr("Everything")
        }

    fun range(store: AppStore, today: String = Day.today): Pair<String, String> = when (this) {
        YEAR -> LocalDate.parse(today).withDayOfYear(1).toString() to today
        ALL -> (listOfNotNull(store.entries.minOfOrNull { it.date }, store.events.minOfOrNull { it.date },
            store.dreams.minOfOrNull { it.date }).minOrNull() ?: today).coerceAtMost(today) to today
        else -> Day.adding(-(days!! - 1), today) to today
    }
}
