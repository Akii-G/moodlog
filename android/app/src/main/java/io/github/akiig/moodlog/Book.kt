package io.github.akiig.moodlog

import android.graphics.Canvas
import android.graphics.Color
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

/** The periods a book can cover. */
enum class BookPeriod {
    ALL, YEAR, TWELVE_MONTHS, THREE_MONTHS;

    val label: String
        get() = when (this) {
            ALL -> tr("All of it")
            YEAR -> tr("This year")
            TWELVE_MONTHS -> tr("The last 12 months")
            THREE_MONTHS -> tr("The last 3 months")
        }

    /** First day of the period; null means from the first day. */
    fun start(today: String = Day.today): String? = when (this) {
        ALL -> null
        YEAR -> today.take(4) + "-01-01"
        TWELVE_MONTHS -> Day.adding(-364, today)
        THREE_MONTHS -> Day.adding(-90, today)
    }
}

/**
 * Your diary as a book: the notes month by month, each day with its mood faces, feeling words,
 * activities and events, as an A4 PDF to keep or print. The same pages as Sottovoce on the PC.
 * Moments with only a face and nothing written or tagged are left out, so it reads as a diary.
 */
class Book(private val store: AppStore, start: String?, end: String, private val includePrivate: Boolean) {
    data class Page(val day: String, val entries: List<Entry>, val events: List<MoodEvent>)

    val pages: List<Page> = (store.entries.map { it.date } + store.events.map { it.date }).toSortedSet()
        .filter { (start == null || it >= start) && it <= end }
        .mapNotNull { day ->
            val entries = store.entriesOn(day).filter { e ->
                (includePrivate || !e.private) && (e.note.isNotBlank() || e.feelings.isNotEmpty() || e.activities.isNotEmpty())
            }
            val events = store.events.filter { it.date == day }
            if (entries.isEmpty() && events.isEmpty()) null else Page(day, entries, events)
        }

    private val W = Report.W
    private val H = Report.H
    private val M = Report.M
    private lateinit var pages_: Report.Pages
    private var open = false
    private lateinit var c: Canvas
    private var pageNo = 0
    private var y = M

    private fun paint(size: Float, color: Int, bold: Boolean = false, italic: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else if (italic) Typeface.ITALIC else Typeface.NORMAL)
        }

    private fun layout(s: String, size: Float, color: Int, bold: Boolean, italic: Boolean, width: Float,
                       align: Layout.Alignment) =
        StaticLayout.Builder.obtain(s, 0, s.length, paint(size, color, bold, italic), width.toInt().coerceAtLeast(1))
            .setAlignment(align).setLineSpacing(0f, 1.15f).build()

    private fun text(s: String, x: Float, top: Float, size: Float, color: Int, bold: Boolean = false,
                     width: Float = W - M - x, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
                     italic: Boolean = false): Float {
        val l = layout(s, size, color, bold, italic, width, align)
        c.save(); c.translate(x, top); l.draw(c); c.restore()
        return l.height.toFloat()
    }

    private fun measure(s: String, size: Float, width: Float, italic: Boolean = false) =
        layout(s, size, Report.ink, false, italic, width, Layout.Alignment.ALIGN_NORMAL).height.toFloat()

    private fun footer() {
        if (pageNo > 1) text("$pageNo", M, H - 28f, 8f, Report.muted, width = W - 2 * M, align = Layout.Alignment.ALIGN_CENTER)
    }

    private fun newPage() {
        if (open) { footer(); pages_.end() }
        pageNo++
        c = pages_.start(pageNo)
        c.drawColor(Color.WHITE)
        open = true
        y = M
    }

    private fun ensure(h: Float) { if (y + h > H - 60f) newPage() }

    /** The same line-drawn face as everywhere in Sottovoce, in the mood's colour. */
    private fun face(cx: Float, cy: Float, r: Float, mood: Int) {
        val color = Report.moods[mood] ?: Report.muted
        val lw = maxOf(1.2f, r * 0.13f)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = lw; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL_AND_STROKE; strokeWidth = lw; strokeJoin = Paint.Join.ROUND }
        c.drawCircle(cx, cy, r, stroke)
        val ex = r * 0.36f; val ey = r * 0.20f; val er = maxOf(0.9f, r * 0.11f)
        for (sx in listOf(-1f, 1f)) c.drawCircle(cx + sx * ex, cy - ey, er, Paint(fill).apply { style = Paint.Style.FILL })
        val w = r * 0.46f; val my = cy + r * 0.30f
        val p = Path()
        when (mood) {
            1 -> {
                for (sx in listOf(-1f, 1f)) {
                    p.moveTo(cx + sx * (ex + r * 0.18f), cy - ey - r * 0.22f)
                    p.lineTo(cx + sx * (ex - r * 0.14f), cy - ey - r * 0.36f)
                }
                p.moveTo(cx - w, my + r * 0.22f)
                p.cubicTo(cx - w * 0.5f, my - r * 0.26f, cx + w * 0.5f, my - r * 0.26f, cx + w, my + r * 0.22f)
                c.drawPath(p, stroke)
            }
            2 -> {
                p.moveTo(cx - w, my + r * 0.12f)
                p.cubicTo(cx - w * 0.5f, my - r * 0.12f, cx + w * 0.5f, my - r * 0.12f, cx + w, my + r * 0.12f)
                c.drawPath(p, stroke)
            }
            3 -> c.drawLine(cx - w, my + r * 0.04f, cx + w, my + r * 0.04f, stroke)
            4 -> {
                p.moveTo(cx - w, my - r * 0.08f)
                p.cubicTo(cx - w * 0.5f, my + r * 0.24f, cx + w * 0.5f, my + r * 0.24f, cx + w, my - r * 0.08f)
                c.drawPath(p, stroke)
            }
            else -> {
                p.moveTo(cx - w * 1.1f, my - r * 0.14f)
                p.lineTo(cx + w * 1.1f, my - r * 0.14f)
                p.cubicTo(cx + w * 0.8f, my + r * 0.44f, cx - w * 0.8f, my + r * 0.44f, cx - w * 1.1f, my - r * 0.14f)
                p.close()
                c.drawPath(p, fill)
            }
        }
    }

    /** Writes the PDF. */
    fun write(file: File): File {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        draw(object : Report.Pages {
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
    fun draw(target: Report.Pages): Int {
        pages_ = target
        if (pages.isEmpty()) return 0
        val inner = W - 2 * M - 16f
        val center = Layout.Alignment.ALIGN_CENTER
        newPage()
        y = 300f
        y += text(tr("My diary"), M, y, 30f, Report.ink, bold = true, width = W - 2 * M, align = center) + 18f
        val a = Day.pretty(pages.first().day, "LLLL yyyy")
        val b = Day.pretty(pages.last().day, "LLLL yyyy")
        y += text(if (a == b) a else "$a – $b", M, y, 13f, Report.ink2, width = W - 2 * M, align = center) + 8f
        text(if (pages.size == 1) tr("1 day") else tr("%@ days", pages.size), M, y, 10f, Report.muted, width = W - 2 * M, align = center)
        text(tr("Written sottovoce, made with Sottovoce"), M, H - 120f, 9f, Report.muted, width = W - 2 * M, align = center)
        newPage()

        var month = ""
        val rule = Paint().apply { color = Report.grid }
        for (page in pages) {
            if (page.day.take(7) != month) {
                if (month.isNotEmpty()) newPage()
                month = page.day.take(7)
                y += text(Day.pretty(page.day, "LLLL yyyy"), M, y, 22f, Report.ink, bold = true) + 10f
                c.drawRect(RectF(M, y, W - M, y + 0.8f), rule)
                y += 16f
            }
            ensure(60f)
            val ms = store.entriesOn(page.day).filter { it.mood > 0 && (includePrivate || !it.private) }.map { it.mood }
            if (ms.isNotEmpty()) {
                c.drawCircle(M + 5f, y + 8f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Report.moods[Mood.level(ms.average())] ?: Report.muted
                })
            }
            y += text(Day.pretty(page.day, "EEEE d MMMM"), M + 16f, y, 12f, Report.ink, bold = true) + 8f
            for (ev in page.events) {
                val line = "${EventKind.label(ev.kind)} · ${ev.title}" + (if (ev.note.isBlank()) "" else " — ${ev.note.trim()}")
                ensure(measure(line, 9.5f, inner, italic = true) + 6f)
                y += text(line, M + 16f, y, 9.5f, Report.ink2, width = inner, italic = true) + 6f
            }
            for (e in page.entries) {
                val head = listOf(e.time, if (e.mood > 0) Mood.name(e.mood) else "").filter { it.isNotEmpty() }.joinToString(" · ")
                if (head.isNotEmpty()) {
                    ensure(18f)
                    var x = M + 16f
                    if (e.mood > 0) { face(M + 22f, y + 6.5f, 6f, e.mood); x = M + 34f }
                    text(head, x, y + 0.5f, 8.5f, Report.muted)
                    y += 17f
                }
                for (para in e.note.split("\n").map { it.trim() }.filter { it.isNotEmpty() }) {
                    ensure(measure(para, 10.5f, inner) + 4f)
                    y += text(para, M + 16f, y, 10.5f, Report.ink, width = inner) + 4f
                }
                val words = listOf(Feeling.text(e.feelings), Activity.text(e.activities)).filter { it.isNotEmpty() }.joinToString(" · ")
                if (words.isNotEmpty()) {
                    ensure(measure(words, 8.5f, inner) + 4f)
                    y += text(words, M + 16f, y, 8.5f, Report.muted, width = inner) + 4f
                }
                y += 8f
            }
            y += 10f
        }
        footer()
        pages_.end()
        return pageNo
    }
}
