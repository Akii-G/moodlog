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
    private val points: List<TalkPoint> = emptyList(),
    private val dose: DoseFeed? = null,   // medication from Dose on the PC, when included
) {
    companion object {
        const val W = 595f
        const val H = 842f
        const val M = 42f
        val ink = Color.parseColor("#1b1b1b")
        val ink2 = Color.parseColor("#4a4945")
        val muted = Color.parseColor("#6d6b66")
        val grid = Color.parseColor("#e3e2dc")
        val axis = Color.parseColor("#c3c2b7")
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
        t(tr("Made with Sottovoce · self-reported data, not a medical record"), M, H - M + 4f, 7.5f, muted)
        t(tr("Page %@", pageNo), W - M, H - M + 4f, 7.5f, muted, align = 2)
    }

    /** A new page when `height` more doesn't fit above the footer, like the PC's. */
    private fun ensure(height: Float) {
        if (y + height > H - M - 18f) newPage()
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

    // The same report as Sottovoce on the PC (export_report in src/moodlog), drawn the same way:
    // same sections, sizes, columns and wording. Keep the two in step.

    /** Text like the PC's: one line placed by its left edge (align 0), centre (1) or right
     *  edge (2) when no width is given, or wrapped in `width`. Returns (width, height). */
    private fun t(s: String, x: Float, top: Float, size: Float, color: Int = ink, bold: Boolean = false,
                  width: Float? = null, align: Int = 0, italic: Boolean = false, measure: Boolean = false): Pair<Float, Float> {
        val p = paint(size, color, bold, italic)
        if (width == null) {
            val w = p.measureText(s)
            val l = StaticLayout.Builder.obtain(s, 0, s.length, p, (w + 4f).toInt().coerceAtLeast(1)).setLineSpacing(0f, 1.03f).setIncludePad(false).build()
            if (!measure) {
                val dx = when (align) { 1 -> w / 2; 2 -> w; else -> 0f }
                c.save(); c.translate(x - dx, top); l.draw(c); c.restore()
            }
            return w to l.height.toFloat()
        }
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(1))
            .setAlignment(when (align) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL }).setLineSpacing(0f, 1.03f).setIncludePad(false).build()
        if (!measure) { c.save(); c.translate(x, top); l.draw(c); c.restore() }
        return width to l.height.toFloat()
    }

    private fun hline(x1: Float, x2: Float, yy: Float, color: Int, w: Float) = c.drawLine(x1, yy, x2, yy, stroke(color, w).apply { strokeCap = Paint.Cap.BUTT })
    private fun date(d: String) = LocalDate.parse(d)
    private fun avg(v: List<Double>) = if (v.isEmpty()) 0.0 else v.average()
    private fun f1(v: Double) = String.format(java.util.Locale.ROOT, "%.1f", v)

    /** Draws every page; returns how many. */
    fun draw(target: Pages): Int {
        pages = target
        newPage()
        val span = days.size
        val moodDays = days.filter { store.average(it) != null }
        val entries = days.flatMap { d -> store.entriesOn(d).filter { it.mood > 0 }.map { it.mood } }
        val evs = store.events.filter { it.date in start..end }.sortedBy { it.date }
        val number = evs.withIndex().associate { (i, e) -> e.id to i + 1 }
        val sleepDays = days.filter { it in sleep }
        val W = Report.W; val M = Report.M

        // ---- title
        t(tr("Mood report"), M, y, 20f, ink, bold = true)
        y += 30f
        t(tr("%@ – %@ · %@ days · made with Sottovoce on %@", Day.pretty(start, "d MMMM yyyy"), Day.pretty(end, "d MMMM yyyy"),
            span, Day.pretty(Day.today, "d MMMM yyyy")), M, y, 9f, muted)
        y += 24f

        // ---- things to talk about
        if (points.isNotEmpty()) {
            t(tr("Things I want to talk about"), M, y, 12f, ink, bold = true)
            y += 20f
            for (p in points) {
                val h = t(p.text, M + 14f, 0f, 10f, width = W - 2 * M - 14f, measure = true).second
                ensure(h + 6f)
                t("•", M + 2f, y, 10f, ink)
                t(p.text, M + 14f, y, 10f, ink, width = W - 2 * M - 14f)
                y += h + 6f
            }
            y += 14f
        }

        // ---- key numbers
        val avgMood = avg(moodDays.mapNotNull { store.average(it) })
        val tiles = mutableListOf<Triple<String, String, Int?>>(
            Triple(tr("Days with a mood"), tr("%@ of %@", moodDays.size, span), null),
            Triple(tr("Average mood (1–5)"), if (avgMood > 0) "${f1(avgMood)}  ${Mood.name(Mood.level(avgMood))}" else "–",
                if (avgMood > 0) Mood.level(avgMood) else null),
            Triple(tr("Moods logged"), "${entries.size}", null))
        for (energyScale in listOf(true, false)) {
            val v = avg(days.mapNotNull { store.scaleAverage(it, energyScale) })
            val words = if (energyScale) Scale.energyWords else Scale.anxietyWords
            tiles.add(Triple(if (energyScale) tr("Average energy (1–5)") else tr("Average anxiety (1–5)"),
                if (v > 0) "${f1(v)}  ${words[Mood.level(v)]}" else "–", null))
        }
        if (sleepDays.isNotEmpty()) tiles.add(Triple(tr("Average sleep"),
            tr("%@ h  (%@ nights)", f1(avg(sleepDays.map { sleep[it]!! })), sleepDays.size), null))
        val cols = 3; val gap = 10f; val th = 50f
        val tw = (W - 2 * M - gap * (cols - 1)) / cols
        tiles.forEachIndexed { i, (label, value, level) ->
            val x = M + (i % cols) * (tw + gap); val yy = y + (i / cols) * (th + gap)
            c.drawRoundRect(RectF(x, yy, x + tw, yy + th), 6f, 6f, fill(tile))
            if (level != null) c.drawRoundRect(RectF(x, yy, x + 5f, yy + th), 2f, 2f, fill(moods[level]!!))
            t(label, x + 12f, yy + 8f, 8f, muted)
            t(value, x + 12f, yy + 22f, 13f, ink, bold = true)
        }
        y += ((tiles.size + cols - 1) / cols) * (th + gap) + 8f

        // ---- mood mix (one stacked bar)
        if (entries.isNotEmpty()) {
            t(tr("How often each mood was logged"), M, y, 10f, ink, bold = true)
            y += 17f
            val total = entries.size; var x = M; val bw = W - 2 * M; val bh = 14f
            for (level in 1..5) {
                val n = entries.count { it == level }
                if (n == 0) continue
                val seg = bw * n / total
                c.drawRect(x, y, x + maxOf(seg - 2f, 1f), y + bh, fill(moods[level]!!))
                x += seg
            }
            y += bh + 6f
            x = M
            for (level in 1..5) {
                val n = entries.count { it == level }
                c.drawRect(x, y + 2f, x + 8f, y + 10f, fill(moods[level]!!))
                val w = t("${Mood.name(level)} ${Math.round(100.0 * n / total)}%", x + 12f, y, 8.5f, ink2).first
                x += w + 26f
            }
            y += 22f
        }

        // ---- feeling words and activities (not from private entries)
        val words = Feeling.counts(store, start, end)
        if (words.isNotEmpty()) {
            val line = tr("Feeling words picked most often: %@.", words.take(8).joinToString(", ") { "${Feeling.label(it.first)} (${it.second})" })
            val h = t(line, M, y, 9f, ink2, width = W - 2 * M).second
            y += h + 16f
        }
        val did = Activity.counts(store, start, end)
        if (did.isNotEmpty()) {
            val line = tr("Activities logged most often: %@.", did.take(8).joinToString(", ") { "${Activity.label(it.first)} (${it.second})" })
            val h = t(line, M, y, 9f, ink2, width = W - 2 * M).second
            y += h + 16f
        }

        // ---- time charts
        val weekly = span > 120
        fun buckets(values: Map<String, Double>): Pair<List<Pair<Int, Double>>, Int> {
            if (!weekly) return days.mapIndexedNotNull { i, d -> values[d]?.let { i to it } } to span
            val weeks = sortedMapOf<Int, MutableList<Double>>()
            for ((d, v) in values) if (d in start..end) weeks.getOrPut(days.indexOf(d) / 7) { mutableListOf() }.add(v)
            return weeks.map { (w, v) -> w to v.average() } to (span + 6) / 7
        }
        fun xpos(i: Int, n: Int, left: Float, width: Float) = left + if (n <= 1) width / 2 else 6f + i.toFloat() / (n - 1) * (width - 12f)
        fun axisTicks(n: Int, left: Float, width: Float, top: Float, height: Float) {
            days.forEachIndexed { i, d ->
                val ld = date(d)
                val show: Boolean; val label: String
                if (span <= 45) { show = ld.dayOfWeek.value == 1 || i == 0; label = Day.pretty(d, "d MMM") }
                else { show = ld.dayOfMonth == 1 || i == 0; label = Day.pretty(d, if (ld.monthValue == 1 || i == 0) "MMM yyyy" else "MMM") }
                if (show) t(label, xpos(if (weekly) i / 7 else i, n, left, width), top + height + 4f, 7.5f, muted, align = 1)
            }
            for (e in evs) {
                val i = days.indexOf(e.date)
                val x = Math.round(xpos(if (weekly) i / 7 else i, n, left, width)) + 0.5f
                c.drawLine(x, top, x, top + height, stroke(ink2, 0.8f).apply { alpha = 178; strokeCap = Paint.Cap.BUTT
                    pathEffect = DashPathEffect(floatArrayOf(2.5f, 2.5f), 0f) })
                c.drawCircle(x, top - 6f, 5.5f, fill(ink))
                t("${number[e.id]}", x, top - 11f, 6.5f, Color.WHITE, bold = true, align = 1)
            }
        }
        fun chartFrame(title: String, legend: List<Pair<String, Int>> = emptyList(), height: Float = 118f, note: String? = null): Pair<Float, Float> {
            ensure(height + 52f)
            t(title, M, y, 10f, ink, bold = true)
            var x = W - M
            for ((name, color) in legend.reversed()) {
                val w = t(name, x, y + 1f, 8f, ink2, align = 2).first
                c.drawLine(x - w - 18f, y + 6f, x - w - 5f, y + 6f, stroke(color, 2f).apply { strokeCap = Paint.Cap.BUTT })
                x -= w + 30f
            }
            val top = y + 26f
            y = top + height + 20f
            if (note != null) { t(note, M, y - 4f, 7.5f, muted, italic = true); y += 10f }
            y += 10f
            return top to height
        }
        fun grid(top: Float, height: Float, left: Float, width: Float, labels: List<String>) {
            labels.forEachIndexed { k, label ->
                val yy = Math.round(top + height - k / 4f * height) + 0.5f
                hline(left, left + width, yy, grid, 0.6f)
                t(label, left - 8f, yy - 5f, 7.5f, muted, align = 2)
            }
        }
        fun lines(pts: List<Pair<Int, Double>>, n: Int, left: Float, width: Float, top: Float, height: Float, color: Int, lw: Float) {
            val path = Path(); var prev: Int? = null
            for ((i, v) in pts) {
                val x = xpos(i, n, left, width); val yy = top + height - ((v - 1) / 4 * height).toFloat()
                if (prev != null && i - prev == 1) path.lineTo(x, yy) else path.moveTo(x, yy)
                prev = i
            }
            c.drawPath(path, stroke(color, lw).apply { strokeCap = Paint.Cap.BUTT; strokeJoin = Paint.Join.MITER })
        }

        val left = M + 44f; val width = W - 2 * M - 44f
        if (moodDays.isNotEmpty()) {
            val (top, height) = chartFrame(tr("Mood, %@", if (weekly) tr("weekly average") else tr("daily average")),
                note = if (evs.isNotEmpty()) tr("Numbered marks are the events listed below.") else null)
            grid(top, height, left, width, (1..5).map { Mood.name(it) })
            val (pts, n) = buckets(moodDays.associateWith { store.average(it)!! })
            lines(pts, n, left, width, top, height, ink2, 1.3f)
            for ((i, v) in pts) {
                val x = xpos(i, n, left, width); val yy = top + height - ((v - 1) / 4 * height).toFloat()
                c.drawCircle(x, yy, 3.2f, fill(Color.WHITE))
                c.drawCircle(x, yy, 2.3f, fill(moods[Mood.level(v)]!!))
            }
            axisTicks(n, left, width, top, height)
        }
        val scaleValues = listOf(true, false).associateWith { e -> days.mapNotNull { d -> store.scaleAverage(d, e)?.let { d to it } }.toMap() }
        if (scaleValues.values.any { it.isNotEmpty() }) {
            val (top, height) = chartFrame(tr("Energy and anxiety (1–5)"), legend = listOf(tr("Energy") to energy, tr("Anxiety") to anxiety),
                height = 96f, note = tr("Energy: 1 very low, 5 very high. Anxiety: 1 calm, 5 very anxious."))
            grid(top, height, left, width, (1..5).map { "$it" })
            var n = span
            for (e in listOf(true, false)) {
                val color = if (e) energy else anxiety
                val (pts, nn) = buckets(scaleValues[e]!!)
                n = nn
                lines(pts, nn, left, width, top, height, color, 1.4f)
                for ((i, v) in pts) c.drawCircle(xpos(i, nn, left, width), top + height - ((v - 1) / 4 * height).toFloat(), 1.8f, fill(color))
            }
            axisTicks(if (weekly) (span + 6) / 7 else span, left, width, top, height)
        }
        if (sleepDays.isNotEmpty()) {
            val (top, height) = chartFrame(tr("Sleep (hours a night%@)", if (weekly) tr(", weekly average") else ""), height = 86f)
            val (pts, n) = buckets(sleepDays.associateWith { sleep[it]!! })
            val hi = maxOf(10, (Math.ceil(pts.maxOf { it.second } / 2) * 2).toInt())
            for (tick in 0..hi step (if (hi <= 12) 2 else 4)) {
                val yy = Math.round(top + height - tick.toFloat() / hi * height) + 0.5f
                hline(left, left + width, yy, if (tick == 0) axis else grid, 0.6f)
                t("$tick", left - 8f, yy - 5f, 7.5f, muted, align = 2)
            }
            val bw = ((width - 12f) / maxOf(n, 1) - 1.5f).coerceIn(1.5f, 10f)
            for ((i, v) in pts) {
                val x = xpos(i, n, left, width); val bh = (v / hi * height).toFloat()
                val r = minOf(2f, bw / 2)
                val path = Path().apply {
                    addRoundRect(RectF(x - bw / 2, top + height - bh, x + bw / 2, top + height),
                        floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW) }
                c.drawPath(path, fill(health))
            }
            axisTicks(n, left, width, top, height)
        }

        // ---- events
        if (evs.isNotEmpty()) {
            ensure(70f)
            t(tr("Events"), M, y, 12f, ink, bold = true)
            y += 22f
            val colx = listOf(M, M + 22f, M + 92f, M + 190f, M + 410f, M + 466f)
            val heads = listOf("#", tr("Date"), tr("Type"), tr("What happened"), tr("Mood %@w before", 2), tr("Mood %@w after", 2))
            fun head() {
                colx.zip(heads).forEach { (x, h) -> t(h, x, y, 7.5f, muted, bold = true, width = if (x >= colx[4]) 50f else null) }
                y += 22f
                hline(M, W - M, y - 4f, axis, 0.6f)
            }
            head()
            for (e in evs) {
                val what = e.title + if (e.note.isNotBlank()) "\n" + e.note.trim() else ""
                val h = t(what, colx[3], 0f, 8.5f, width = 210f, measure = true).second
                if (y + h + 10f > H - M - 18f) { newPage(); head() }
                val yy = y
                t("${number[e.id]}", colx[0], yy, 8.5f, ink, bold = true)
                t(Day.pretty(e.date, "d MMM yyyy"), colx[1], yy, 8.5f, ink)
                t(EventKind.label(e.kind), colx[2], yy, 8.5f, ink, width = 95f)
                t(what, colx[3], yy, 8.5f, ink, width = 210f)
                if (e.date <= Day.today) {
                    val around = store.moodAround(e.date)
                    for ((x, win) in listOf(colx[4] to around.before, colx[5] to around.after)) {
                        if (win.days >= 3) {
                            c.drawRect(x, yy + 2f, x + 7f, yy + 9f, fill(moods[Mood.level(win.average)]!!))
                            t("${f1(win.average)} (${win.days} d)", x + 10f, yy, 8.5f, ink)
                        } else t("–", x + 10f, yy, 8.5f, muted)
                    }
                    if (!around.complete) t(tr("so far"), colx[5] + 10f, yy + 11f, 7f, muted, italic = true)
                }
                y += maxOf(h, 22f) + 6f
                hline(M, W - M, y - 3f, grid, 0.5f)
            }
            y += 12f
        }

        // ---- medication (from Dose): current ones, then the ones no longer taken
        val medDoses = dose?.between(start, end) ?: emptyList()
        if (medDoses.isNotEmpty()) {
            data class Row(var taken: Int = 0, var skipped: Int = 0, var missed: Int = 0, var upcoming: Int = 0,
                           var first: String = "", var last: String = "")
            val rows = sortedMapOf<String, Row>(String.CASE_INSENSITIVE_ORDER)
            for (d in medDoses) {
                val r = rows.getOrPut(d.name) { Row(first = d.date, last = d.date) }
                when {
                    d.status == "taken" -> r.taken++
                    d.status == "skipped" -> r.skipped++
                    d.missed() -> r.missed++
                    else -> r.upcoming++
                }
                if (d.date < r.first) r.first = d.date
                if (d.date > r.last) r.last = d.date
            }
            val archived = dose!!.medicines.filter { it.archived }.map { it.name }.toSet()
            val asNeeded = dose.medicines.filter { it.asNeeded }.map { it.name }.toSet()
            val current = rows.keys.filter { it !in archived }
            val previous = rows.keys.filter { it in archived }
            val note = tr("As marked in Dose.") + " " + tr("Not taken: doses that were due and never marked. Yet to take: due later today.")
            val colx = listOf(M, M + 182f, M + 232f, M + 282f, M + 334f, M + 390f, M + 452f)
            val heads = listOf(tr("Medication"), tr("Taken"), tr("Skipped"), tr("Not taken"), tr("Yet to take"),
                tr("First in period"), tr("Last in period"))
            fun head() {
                colx.zip(heads).forEach { (x, h) -> t(h, x, y, 7.5f, muted, bold = true, width = if (x > colx[0]) 58f else null) }
                y += 22f
                hline(M, W - M, y - 3f, axis, 0.6f)
            }
            for ((title, names) in listOf(tr("Medication") to current, tr("Previous medication") to previous)) {
                if (names.isEmpty()) continue
                ensure(80f)
                t(title, M, y, 12f, ink, bold = true)
                y += 20f
                if (names == current || current.isEmpty()) y += t(note, M, y, 8f, muted, width = W - 2 * M).second + 8f
                head()
                for (name in names) {
                    val r = rows[name]!!
                    val label = name + if (name in asNeeded) " · " + tr("as needed") else ""
                    val h = t(label, colx[0], 0f, 8.5f, width = 172f, measure = true).second
                    if (y + h + 8f > H - M - 18f) { newPage(); head() }
                    val yy = y
                    t(label, colx[0], yy, 8.5f, ink, width = 172f)
                    colx.subList(1, 5).zip(listOf(r.taken, r.skipped, r.missed, r.upcoming)).forEach { (x, n) ->
                        t(if (n > 0) "$n" else "–", x, yy, 8.5f, if (n > 0) ink else muted)
                    }
                    t(Day.pretty(r.first, "d MMM yyyy"), colx[5], yy, 8.5f, ink)
                    t(Day.pretty(r.last, "d MMM yyyy"), colx[6], yy, 8.5f, ink)
                    y += maxOf(h, 12f) + 6f
                }
                y += 10f
            }
        }

        // ---- questionnaires (PHQ-9, GAD-7)
        val taken = if (includeSurveys) store.surveys.filter { it.date in start..end && !it.private }.sortedBy { it.date } else emptyList()
        if (taken.isNotEmpty()) {
            ensure(90f)
            t(tr("Questionnaires"), M, y, 12f, ink, bold = true)
            y += 20f
            val note = tr("Answered in Sottovoce. Each answer counts 0 (not at all) to 3 (nearly every day); the score is their sum, out of 27 for PHQ-9 and 21 for GAD-7. The answers are listed in question order. Sottovoce gives no interpretation.")
            y += t(note, M, y, 8f, muted, width = W - 2 * M).second + 8f
            val colx = listOf(M, M + 110f, M + 180f, M + 240f, M + 370f)
            val heads = listOf(tr("Date"), tr("Questionnaire"), tr("Score"), tr("Answers"), tr("Difficulty"))
            fun head() {
                colx.zip(heads).forEach { (x, h) -> t(h, x, y, 7.5f, muted, bold = true) }
                y += 14f
                hline(M, W - M, y - 3f, axis, 0.6f)
            }
            head()
            val diffWords = SurveyText.current.difficultyOptions
            for (s in taken) {
                val diff = if (s.difficulty >= 0) diffWords[s.difficulty] else "–"
                val h = t(diff, colx[4], 0f, 8.5f, width = W - M - colx[4], measure = true).second
                if (y + h + 8f > H - M - 18f) { newPage(); head() }
                val yy = y
                t(Day.pretty(s.date, "d MMM yyyy"), colx[0], yy, 8.5f, ink)
                t(s.name, colx[1], yy, 8.5f, ink)
                t("${s.score} / ${s.maxScore}", colx[2], yy, 8.5f, ink, bold = true)
                t(s.answers.joinToString(" "), colx[3], yy, 8.5f, ink)
                t(diff, colx[4], yy, 8.5f, ink, width = W - M - colx[4])
                y += maxOf(h, 12f) + 6f
            }
            val credit = SurveyText.current.credit
            val h = t(credit, M, 0f, 7f, width = W - 2 * M, measure = true).second
            ensure(h + 6f)
            t(credit, M, y + 2f, 7f, muted, width = W - 2 * M, italic = true)
            y += h + 16f
        }

        // ---- dreams
        val dreamSummary = if (includeDreams) store.dreamSummary(start, end) else null
        if (dreamSummary != null) {
            ensure(70f)
            t(tr("Dreams"), M, y, 12f, ink, bold = true)
            y += 22f
            y += t(dreamSummary, M, y, 9f, ink2, width = W - 2 * M).second + 18f
        }

        // ---- day by day
        val showDreams = includeDreams && store.dreams.any { it.date in start..end }
        val rows = days.filter { d -> store.entriesOn(d).isNotEmpty() || store.eventsOn(d).isNotEmpty() || d in sleep ||
            (showDreams && store.dreamsOn(d).isNotEmpty()) }
        if (rows.isNotEmpty()) {
            ensure(80f)
            t(tr("Day by day"), M, y, 12f, ink, bold = true)
            y += 22f
            val hasSleep = sleepDays.isNotEmpty()
            val dcols = mutableListOf(tr("Date") to 74f, tr("Mood") to 92f, tr("Energy") to 40f, tr("Anxiety") to 44f)
            if (hasSleep) dcols.add(tr("Sleep") to 38f)
            dcols.add(tr("Event") to 34f)
            if (showDreams) dcols.add(tr("Dream") to 52f)
            val withText = includeNotes || (showDreams && dreamText)
            if (withText) {
                val heading = if (includeNotes && dreamText && showDreams) tr("Notes and dreams") else if (includeNotes) tr("Notes") else tr("Dreams")
                dcols.add(heading to W - 2 * M - dcols.sumOf { it.second.toDouble() }.toFloat())
            }
            val xs = dcols.runningFold(M) { acc, col -> acc + col.second }.dropLast(1)
            fun head() {
                dcols.zip(xs).forEach { (col, x) -> t(col.first, x, y, 7.5f, muted, bold = true) }
                y += 14f
                hline(M, W - M, y - 3f, axis, 0.6f)
            }
            head()
            for (d in rows) {
                val items = store.entriesOn(d)
                val lines = mutableListOf<String>()
                if (includeNotes) lines += items.filter { it.note.isNotBlank() && !it.private }
                    .map { "${it.time}  ${it.note.split(Regex("\\s+")).filter { w -> w.isNotEmpty() }.joinToString(" ")}".trim() }
                val dayDreams = if (showDreams) store.dreamsOn(d) else emptyList()
                if (dreamText) lines += dayDreams.filter { it.text.isNotBlank() && !it.private }
                    .map { tr("Dream: %@", it.text.split(Regex("\\s+")).filter { w -> w.isNotEmpty() }.joinToString(" ")) }
                val notes = lines.joinToString("\n")
                var h = 12f
                if (notes.isNotEmpty()) h = maxOf(t(notes, xs.last(), 0f, 7.5f, width = dcols.last().second - 4f, measure = true).second, 12f)
                if (y + h + 6f > H - M - 18f) { newPage(); head() }
                val yy = y
                if (date(d).dayOfWeek.value >= 6) c.drawRect(M, yy - 2f, W - M, yy + h + 2f, fill(tile))
                t(Day.pretty(d, "EEE d MMM"), xs[0], yy, 8f, ink)
                val a = store.average(d)
                if (a != null) {
                    c.drawRect(xs[1], yy + 1.5f, xs[1] + 7f, yy + 8.5f, fill(moods[Mood.level(a)]!!))
                    val count = items.count { it.mood > 0 }
                    var label = "${Mood.name(Mood.level(a))} ${f1(a)}"
                    if (count > 1) label += " ($count)"
                    t(label, xs[1] + 11f, yy, 8f, ink)
                }
                listOf(true, false).forEachIndexed { k, e ->
                    val v = store.scaleAverage(d, e)
                    t(v?.let { f1(it).removeSuffix(".0") } ?: "", xs[2 + k], yy, 8f, ink)
                }
                var col = 4
                if (hasSleep) { t(sleep[d]?.let { f1(it) } ?: "", xs[col], yy, 8f, ink); col++ }
                t(evs.filter { it.date == d }.joinToString(", ") { "${number[it.id]}" }, xs[col], yy, 8f, ink, bold = true)
                col++
                if (showDreams && dayDreams.isNotEmpty()) {
                    val mark = when {
                        dayDreams.any { it.isNightmare } -> tr("Nightmare")
                        dayDreams.any { it.feeling == "pleasant" } -> tr("Pleasant")
                        dayDreams.all { "forgotten" in it.tags && it.text.isBlank() } -> tr("Forgotten")
                        else -> tr("Yes")
                    }
                    t(mark, xs[col], yy, 7.5f, ink, bold = mark == tr("Nightmare"))
                }
                if (notes.isNotEmpty()) t(notes, xs.last(), yy, 7.5f, ink2, width = dcols.last().second - 4f)
                y += h + 5f
            }
            y += 6f
        }

        footer(); target.end(); open = false
        return pageNo
    }
}

/** The report's period choices, like the PC's; CUSTOM uses the dates picked in the dialog. */
enum class ReportPeriod(val days: Int?) {
    WEEK(7), TWO_WEEKS(14), MONTH(30), THREE_MONTHS(91), SIX_MONTHS(182), YEAR(null), ALL(null), CUSTOM(null);

    val label: String
        get() = when (this) {
            WEEK -> tr("Last 7 days")
            TWO_WEEKS -> tr("Last 2 weeks")
            MONTH -> tr("Last 30 days")
            THREE_MONTHS -> tr("Last 3 months")
            SIX_MONTHS -> tr("Last 6 months")
            YEAR -> tr("This year")
            ALL -> tr("Everything")
            CUSTOM -> tr("Choose the dates")
        }

    fun range(store: AppStore, today: String = Day.today): Pair<String, String> = when (this) {
        YEAR -> LocalDate.parse(today).withDayOfYear(1).toString() to today
        ALL -> (listOfNotNull(store.entries.minOfOrNull { it.date }, store.events.minOfOrNull { it.date },
            store.dreams.minOfOrNull { it.date }).minOrNull() ?: today).coerceAtMost(today) to today
        CUSTOM -> Day.adding(-29, today) to today
        else -> Day.adding(-(days!! - 1), today) to today
    }
}
