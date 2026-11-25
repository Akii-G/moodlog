import UIKit

/// The report for your doctor: the same sections as Moodlog on the PC and Android — a summary,
/// how often each mood was logged, mood / energy and anxiety / sleep over the period, events
/// with the mood before and after, dreams, and a day-by-day table. A4, printable.
@MainActor
final class Report {
    static let W: CGFloat = 595, H: CGFloat = 842, M: CGFloat = 42
    private let ink = UIColor(hex: 0x1b1b1b), ink2 = UIColor(hex: 0x4a4945), muted = UIColor(hex: 0x6d6b66)
    private let axis = UIColor(hex: 0xc3c2b7)
    private let grid = UIColor(hex: 0xe3e2dc), tile = UIColor(hex: 0xf4f3ef), health = UIColor(hex: 0x1baf7a)
    private let energy = UIColor(hex: 0xb87c10), anxiety = UIColor(hex: 0x7b5ad6)
    private let moods: [Int: UIColor] = [1: UIColor(hex: 0xcf3f3f), 2: UIColor(hex: 0xeb9a94), 3: UIColor(hex: 0xbdbbb3),
                                         4: UIColor(hex: 0x8fbcf0), 5: UIColor(hex: 0x2a78d6)]

    private let store: AppStore
    private let start: String, end: String
    private let includeNotes: Bool, sleep: [String: Double], includeDreams: Bool, dreamText: Bool
    private let days: [String]
    private let doses: [DoseRecord], meds: [MedicationInfo]
    private let surveys: [Survey]
    private let points: [TalkPoint]
    private var ctx: UIGraphicsPDFRendererContext!
    private var pageNo = 0
    private var y: CGFloat = 42

    init(store: AppStore, start: String, end: String, includeNotes: Bool, sleep: [String: Double],
         includeDreams: Bool, dreamText: Bool, doses: [DoseRecord] = [], meds: [MedicationInfo] = [],
         includeSurveys: Bool = false, points: [TalkPoint] = []) {
        self.points = points
        self.store = store; self.start = start; self.end = end; self.includeNotes = includeNotes
        self.sleep = sleep; self.includeDreams = includeDreams; self.dreamText = dreamText
        self.doses = doses.filter { $0.day >= start && $0.day <= end }; self.meds = meds
        self.surveys = includeSurveys ? store.surveys.filter { $0.date >= start && $0.date <= end && !$0.hidden } : []
        var out: [String] = []
        var d = start
        while d <= end { out.append(d); d = Day.adding(1, to: d) }
        days = out
    }

    // MARK: drawing helpers

    private func attrs(_ size: CGFloat, _ color: UIColor, bold: Bool = false, italic: Bool = false,
                       align: NSTextAlignment = .left) -> [NSAttributedString.Key: Any] {
        var font = UIFont.systemFont(ofSize: size, weight: bold ? .bold : .regular)
        if italic, let d = font.fontDescriptor.withSymbolicTraits(.traitItalic) { font = UIFont(descriptor: d, size: size) }
        let p = NSMutableParagraphStyle()
        p.alignment = align
        p.lineHeightMultiple = 1.1
        return [.font: font, .foregroundColor: color, .paragraphStyle: p]
    }

    @discardableResult
    private func text(_ s: String, _ x: CGFloat, _ top: CGFloat, _ size: CGFloat = 9, _ color: UIColor? = nil,
                      bold: Bool = false, width: CGFloat? = nil, align: NSTextAlignment = .left, italic: Bool = false) -> CGFloat {
        let w = width ?? (Report.W - Report.M - x)
        let a = NSAttributedString(string: s, attributes: attrs(size, color ?? ink, bold: bold, italic: italic, align: align))
        let r = a.boundingRect(with: CGSize(width: w, height: 2000), options: [.usesLineFragmentOrigin], context: nil)
        a.draw(with: CGRect(x: x, y: top, width: w, height: ceil(r.height)), options: [.usesLineFragmentOrigin], context: nil)
        return ceil(r.height)
    }

    private func measure(_ s: String, _ size: CGFloat, _ width: CGFloat, bold: Bool = false) -> CGFloat {
        let a = NSAttributedString(string: s, attributes: attrs(size, ink, bold: bold))
        return ceil(a.boundingRect(with: CGSize(width: width, height: 2000), options: [.usesLineFragmentOrigin], context: nil).height)
    }

    private func fill(_ rect: CGRect, _ color: UIColor, radius: CGFloat = 0) {
        color.setFill()
        UIBezierPath(roundedRect: rect, cornerRadius: radius).fill()
    }

    private func line(_ a: CGPoint, _ b: CGPoint, _ color: UIColor, _ width: CGFloat, dashed: Bool = false) {
        let p = UIBezierPath(); p.move(to: a); p.addLine(to: b)
        p.lineWidth = width
        if dashed { p.setLineDash([3, 3], count: 2, phase: 0) }
        color.setStroke(); p.stroke()
    }

    private func dot(_ c: CGPoint, _ r: CGFloat, _ color: UIColor) {
        color.setFill()
        UIBezierPath(ovalIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)).fill()
    }

    private func footer() {
        t(tr("Made with Sottovoce · self-reported data, not a medical record"), Report.M, Report.H - Report.M + 4, 7.5, muted)
        t(tr("Page %@", "\(pageNo)"), Report.W - Report.M, Report.H - Report.M + 4, 7.5, muted, align: 2)
    }

    private func newPage() {
        if pageNo > 0 { footer() }
        ctx.beginPage()
        pageNo += 1
        y = Report.M
    }

    /// A new page when `h` more doesn't fit above the footer, like the PC's.
    private func ensure(_ h: CGFloat) { if y + h > Report.H - Report.M - 18 { newPage() } }

    // MARK: the report

    func write(to url: URL) throws {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: Report.W, height: Report.H))
        try renderer.writePDF(to: url) { context in
            self.ctx = context
            self.drawAll()
            self.footer()
        }
    }

    // The same report as Sottovoce on the PC (export_report in src/moodlog), drawn the same way:
    // same sections, sizes, columns and wording. Keep the two in step.

    /// Text like the PC's: one line placed by its left edge (align 0), centre (1) or right edge
    /// (2) when no width is given, or wrapped in `width`. Returns (width, height).
    @discardableResult
    private func t(_ s: String, _ x: CGFloat, _ top: CGFloat, _ size: CGFloat, _ color: UIColor? = nil, bold: Bool = false,
                   width: CGFloat? = nil, align: Int = 0, italic: Bool = false, measure: Bool = false) -> (CGFloat, CGFloat) {
        let a = NSAttributedString(string: s, attributes: attrs(size, color ?? ink, bold: bold, italic: italic,
                                                                align: width == nil ? NSTextAlignment.left : [NSTextAlignment.left, .center, .right][align]))
        if let width {
            let r = a.boundingRect(with: CGSize(width: width, height: 4000), options: [.usesLineFragmentOrigin], context: nil)
            if !measure { a.draw(with: CGRect(x: x, y: top, width: width, height: ceil(r.height)), options: [.usesLineFragmentOrigin], context: nil) }
            return (width, ceil(r.height))
        }
        let sz = a.size()
        if !measure {
            let dx: CGFloat = align == 1 ? sz.width / 2 : align == 2 ? sz.width : 0
            a.draw(at: CGPoint(x: x - dx, y: top))
        }
        return (ceil(sz.width), ceil(sz.height))
    }

    private func hline(_ x1: CGFloat, _ x2: CGFloat, _ y: CGFloat, _ color: UIColor, _ w: CGFloat) {
        line(CGPoint(x: x1, y: y), CGPoint(x: x2, y: y), color, w)
    }

    private func rect(_ r: CGRect, _ color: UIColor) { color.setFill(); UIRectFill(r) }
    private func avg(_ v: [Double]) -> Double { v.isEmpty ? 0 : v.reduce(0, +) / Double(v.count) }
    private func f1(_ v: Double) -> String { String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), v) }
    private func weekday(_ d: String) -> Int {   // 1 = Monday … 7 = Sunday
        guard let date = Day.date(d) else { return 1 }
        return (Calendar(identifier: .gregorian).component(.weekday, from: date) + 5) % 7 + 1
    }

    private func drawAll() {
        let M = Report.M, W = Report.W, H = Report.H
        newPage()
        let span = days.count
        let moodDays = days.filter { store.average(on: $0) != nil }
        let entries = days.flatMap { d in store.entries(on: d).filter { $0.mood > 0 }.map(\.mood) }
        let evs = store.events.filter { $0.date >= start && $0.date <= end }.sorted { $0.date < $1.date }
        var number: [String: Int] = [:]
        for (i, e) in evs.enumerated() { number[e.id] = i + 1 }
        let sleepDays = days.filter { sleep[$0] != nil }
        let bottom = H - M - 18

        // ---- title
        t(tr("Mood report"), M, y, 20, ink, bold: true)
        y += 30
        t(tr("%@ – %@ · %@ days · made with Sottovoce on %@", Day.pretty(start, style: "d MMMM yyyy"),
             Day.pretty(end, style: "d MMMM yyyy"), "\(span)", Day.pretty(Day.today, style: "d MMMM yyyy")), M, y, 9, muted)
        y += 24

        // ---- things to talk about
        if !points.isEmpty {
            t(tr("Things I want to talk about"), M, y, 12, ink, bold: true)
            y += 20
            for p in points {
                let h = t(p.text, M + 14, 0, 10, width: W - 2 * M - 14, measure: true).1
                ensure(h + 6)
                t("\u{2022}", M + 2, y, 10, ink)
                t(p.text, M + 14, y, 10, ink, width: W - 2 * M - 14)
                y += h + 6
            }
            y += 14
        }

        // ---- key numbers
        let avgMood = avg(moodDays.compactMap { store.average(on: $0) })
        var tiles: [(String, String, Int?)] = [
            (tr("Days with a mood"), tr("%@ of %@", "\(moodDays.count)", "\(span)"), nil),
            (tr("Average mood (1–5)"), avgMood > 0 ? "\(f1(avgMood))  \(Mood.name(Mood.level(avgMood)))" : "–",
             avgMood > 0 ? Mood.level(avgMood) : nil),
            (tr("Moods logged"), "\(entries.count)", nil),
        ]
        for energyScale in [true, false] {
            let v = avg(days.compactMap { store.scaleAverage(on: $0, energy: energyScale) })
            let words = energyScale ? Scale.energyWords : Scale.anxietyWords
            tiles.append((energyScale ? tr("Average energy (1–5)") : tr("Average anxiety (1–5)"),
                          v > 0 ? "\(f1(v))  \(words[Mood.level(v)] ?? "")" : "–", nil))
        }
        if !sleepDays.isEmpty {
            tiles.append((tr("Average sleep"), tr("%@ h  (%@ nights)", f1(avg(sleepDays.map { sleep[$0]! })), "\(sleepDays.count)"), nil))
        }
        let cols = 3, gap: CGFloat = 10, th: CGFloat = 50
        let tw = (W - 2 * M - gap * CGFloat(cols - 1)) / CGFloat(cols)
        for (i, tile) in tiles.enumerated() {
            let x = M + CGFloat(i % cols) * (tw + gap), yy = y + CGFloat(i / cols) * (th + gap)
            fill(CGRect(x: x, y: yy, width: tw, height: th), self.tile, radius: 6)
            if let level = tile.2 { fill(CGRect(x: x, y: yy, width: 5, height: th), moods[level]!, radius: 2) }
            t(tile.0, x + 12, yy + 8, 8, muted)
            t(tile.1, x + 12, yy + 22, 13, ink, bold: true)
        }
        y += CGFloat((tiles.count + cols - 1) / cols) * (th + gap) + 8

        // ---- mood mix (one stacked bar)
        if !entries.isEmpty {
            t(tr("How often each mood was logged"), M, y, 10, ink, bold: true)
            y += 17
            let total = CGFloat(entries.count), bw = W - 2 * M, bh: CGFloat = 14
            var x = M
            for level in 1...5 {
                let n = CGFloat(entries.filter { $0 == level }.count)
                if n == 0 { continue }
                let seg = bw * n / total
                rect(CGRect(x: x, y: y, width: max(seg - 2, 1), height: bh), moods[level]!)
                x += seg
            }
            y += bh + 6
            x = M
            for level in 1...5 {
                let n = Double(entries.filter { $0 == level }.count)
                rect(CGRect(x: x, y: y + 2, width: 8, height: 8), moods[level]!)
                let w = t("\(Mood.name(level)) \(Int((100 * n / Double(total)).rounded()))%", x + 12, y, 8.5, ink2).0
                x += w + 26
            }
            y += 22
        }

        // ---- feeling words and activities (not from private entries)
        let words = Feeling.counts(store, from: start, to: end)
        if !words.isEmpty {
            let line = tr("Feeling words picked most often: %@.", words.prefix(8).map { "\(Feeling.label($0.0)) (\($0.1))" }.joined(separator: ", "))
            y += t(line, M, y, 9, ink2, width: W - 2 * M).1 + 16
        }
        let did = Activity.counts(store, from: start, to: end)
        if !did.isEmpty {
            let line = tr("Activities logged most often: %@.", did.prefix(8).map { "\(Activity.label($0.0)) (\($0.1))" }.joined(separator: ", "))
            y += t(line, M, y, 9, ink2, width: W - 2 * M).1 + 16
        }

        // ---- time charts
        let weekly = span > 120
        func buckets(_ values: [String: Double]) -> ([(Int, Double)], Int) {
            if !weekly { return (days.enumerated().compactMap { i, d in values[d].map { (i, $0) } }, span) }
            var weeks: [Int: [Double]] = [:]
            for (i, d) in days.enumerated() { if let v = values[d] { weeks[i / 7, default: []].append(v) } }
            return (weeks.keys.sorted().map { ($0, avg(weeks[$0]!)) }, (span + 6) / 7)
        }
        func xpos(_ i: Int, _ n: Int, _ left: CGFloat, _ width: CGFloat) -> CGFloat {
            left + (n <= 1 ? width / 2 : 6 + CGFloat(i) / CGFloat(n - 1) * (width - 12))
        }
        func axisTicks(_ n: Int, _ left: CGFloat, _ width: CGFloat, _ top: CGFloat, _ height: CGFloat) {
            for (i, d) in days.enumerated() {
                let dayOfMonth = Int(d.suffix(2)) ?? 0, month = Int(d.dropFirst(5).prefix(2)) ?? 0
                let show: Bool, label: String
                if span <= 45 { show = weekday(d) == 1 || i == 0; label = Day.pretty(d, style: "d MMM") }
                else { show = dayOfMonth == 1 || i == 0; label = Day.pretty(d, style: month == 1 || i == 0 ? "MMM yyyy" : "MMM") }
                if show { t(label, xpos(weekly ? i / 7 : i, n, left, width), top + height + 4, 7.5, muted, align: 1) }
            }
            for e in evs {
                let i = days.firstIndex(of: e.date) ?? 0
                let x = xpos(weekly ? i / 7 : i, n, left, width).rounded() + 0.5
                line(CGPoint(x: x, y: top), CGPoint(x: x, y: top + height), ink2.withAlphaComponent(0.7), 0.8, dashed: true)
                dot(CGPoint(x: x, y: top - 6), 5.5, ink)
                t("\(number[e.id] ?? 0)", x, top - 11, 6.5, .white, bold: true, align: 1)
            }
        }
        func chartFrame(_ title: String, legend: [(String, UIColor)] = [], height: CGFloat = 118, note: String? = nil) -> (CGFloat, CGFloat) {
            ensure(height + 52)
            t(title, M, y, 10, ink, bold: true)
            var x = W - M
            for (name, color) in legend.reversed() {
                let w = t(name, x, y + 1, 8, ink2, align: 2).0
                line(CGPoint(x: x - w - 18, y: y + 6), CGPoint(x: x - w - 5, y: y + 6), color, 2)
                x -= w + 30
            }
            let top = y + 26
            y = top + height + 20
            if let note { t(note, M, y - 4, 7.5, muted, italic: true); y += 10 }
            y += 10
            return (top, height)
        }
        func gridLines(_ top: CGFloat, _ height: CGFloat, _ left: CGFloat, _ width: CGFloat, _ labels: [String]) {
            for (k, label) in labels.enumerated() {
                let yy = (top + height - CGFloat(k) / 4 * height).rounded() + 0.5
                hline(left, left + width, yy, grid, 0.6)
                t(label, left - 8, yy - 5, 7.5, muted, align: 2)
            }
        }
        func lines(_ pts: [(Int, Double)], _ n: Int, _ left: CGFloat, _ width: CGFloat, _ top: CGFloat, _ height: CGFloat,
                   _ color: UIColor, _ lw: CGFloat) {
            let path = UIBezierPath()
            var prev: Int?
            for (i, v) in pts {
                let p = CGPoint(x: xpos(i, n, left, width), y: top + height - CGFloat((v - 1) / 4) * height)
                if let prev, i - prev == 1 { path.addLine(to: p) } else { path.move(to: p) }
                prev = i
            }
            path.lineWidth = lw
            color.setStroke(); path.stroke()
        }

        let left = M + 44, width = W - 2 * M - 44
        if !moodDays.isEmpty {
            let (top, height) = chartFrame(tr("Mood, %@", weekly ? tr("weekly average") : tr("daily average")),
                                           note: evs.isEmpty ? nil : tr("Numbered marks are the events listed below."))
            gridLines(top, height, left, width, (1...5).map { Mood.name($0) })
            var values: [String: Double] = [:]
            for d in moodDays { values[d] = store.average(on: d) }
            let (pts, n) = buckets(values)
            lines(pts, n, left, width, top, height, ink2, 1.3)
            for (i, v) in pts {
                let p = CGPoint(x: xpos(i, n, left, width), y: top + height - CGFloat((v - 1) / 4) * height)
                dot(p, 3.2, .white)
                dot(p, 2.3, moods[Mood.level(v)]!)
            }
            axisTicks(n, left, width, top, height)
        }
        var scaleValues: [Bool: [String: Double]] = [true: [:], false: [:]]
        for e in [true, false] { for d in days { if let v = store.scaleAverage(on: d, energy: e) { scaleValues[e]![d] = v } } }
        if !scaleValues[true]!.isEmpty || !scaleValues[false]!.isEmpty {
            let (top, height) = chartFrame(tr("Energy and anxiety (1–5)"), legend: [(tr("Energy"), energy), (tr("Anxiety"), anxiety)],
                                           height: 96, note: tr("Energy: 1 very low, 5 very high. Anxiety: 1 calm, 5 very anxious."))
            gridLines(top, height, left, width, (1...5).map { "\($0)" })
            for e in [true, false] {
                let color = e ? energy : anxiety
                let (pts, n) = buckets(scaleValues[e]!)
                lines(pts, n, left, width, top, height, color, 1.4)
                for (i, v) in pts { dot(CGPoint(x: xpos(i, n, left, width), y: top + height - CGFloat((v - 1) / 4) * height), 1.8, color) }
            }
            axisTicks(weekly ? (span + 6) / 7 : span, left, width, top, height)
        }
        if !sleepDays.isEmpty {
            let (top, height) = chartFrame(tr("Sleep (hours a night%@)", weekly ? tr(", weekly average") : ""), height: 86)
            var values: [String: Double] = [:]
            for d in sleepDays { values[d] = sleep[d] }
            let (pts, n) = buckets(values)
            let hi = max(10, Int(ceil((pts.map(\.1).max() ?? 0) / 2) * 2))
            for tick in stride(from: 0, through: hi, by: hi <= 12 ? 2 : 4) {
                let yy = (top + height - CGFloat(tick) / CGFloat(hi) * height).rounded() + 0.5
                hline(left, left + width, yy, tick == 0 ? axis : grid, 0.6)
                t("\(tick)", left - 8, yy - 5, 7.5, muted, align: 2)
            }
            let bw = min(10, max(1.5, (width - 12) / CGFloat(max(n, 1)) - 1.5))
            for (i, v) in pts {
                let x = xpos(i, n, left, width), bh = CGFloat(v) / CGFloat(hi) * height
                health.setFill()
                UIBezierPath(roundedRect: CGRect(x: x - bw / 2, y: top + height - bh, width: bw, height: bh),
                             byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: min(2, bw / 2), height: min(2, bw / 2))).fill()
            }
            axisTicks(n, left, width, top, height)
        }

        // ---- events
        if !evs.isEmpty {
            ensure(70)
            t(tr("Events"), M, y, 12, ink, bold: true)
            y += 22
            let colx = [M, M + 22, M + 92, M + 190, M + 410, M + 466]
            let heads = ["#", tr("Date"), tr("Type"), tr("What happened"), tr("Mood %@w before", "2"), tr("Mood %@w after", "2")]
            func head() {
                for (x, h) in zip(colx, heads) { t(h, x, y, 7.5, muted, bold: true, width: x >= colx[4] ? 50 : nil) }
                y += 22
                hline(M, W - M, y - 4, axis, 0.6)
            }
            head()
            for e in evs {
                let note = e.note.trimmingCharacters(in: .whitespacesAndNewlines)
                let what = e.title + (note.isEmpty ? "" : "\n" + note)
                let h = t(what, colx[3], 0, 8.5, width: 210, measure: true).1
                if y + h + 10 > bottom { newPage(); head() }
                let yy = y
                t("\(number[e.id] ?? 0)", colx[0], yy, 8.5, ink, bold: true)
                t(Day.pretty(e.date, style: "d MMM yyyy"), colx[1], yy, 8.5, ink)
                t(EventKind.label(for: e.kind), colx[2], yy, 8.5, ink, width: 95)
                t(what, colx[3], yy, 8.5, ink, width: 210)
                if e.date <= Day.today {
                    let around = store.moodAround(e.date)
                    for (x, win) in [(colx[4], around.before), (colx[5], around.after)] {
                        if win.1 >= 3 {
                            rect(CGRect(x: x, y: yy + 2, width: 7, height: 7), moods[Mood.level(win.0)]!)
                            t("\(f1(win.0)) (\(win.1) d)", x + 10, yy, 8.5, ink)
                        } else {
                            t("–", x + 10, yy, 8.5, muted)
                        }
                    }
                    if !around.complete { t(tr("so far"), colx[5] + 10, yy + 11, 7, muted, italic: true) }
                }
                y += max(h, 22) + 6
                hline(M, W - M, y - 3, grid, 0.5)
            }
            y += 12
        }

        // ---- medication (from Dose and Apple Health): current ones, then the ones no longer taken
        var medRows: [String: (taken: Int, skipped: Int, missed: Int, upcoming: Int, first: String, last: String)] = [:]
        for d in doses {
            var r = medRows[d.name] ?? (0, 0, 0, 0, d.day, d.day)
            if d.status == "taken" { r.taken += 1 }
            else if d.status == "skipped" { r.skipped += 1 }
            else if d.missed { r.missed += 1 }
            else { r.upcoming += 1 }
            r.first = min(r.first, d.day); r.last = max(r.last, d.day)
            medRows[d.name] = r
        }
        if !medRows.isEmpty {
            let archived = Set(meds.filter(\.archived).map(\.name))
            let names = medRows.keys.sorted { $0.lowercased() < $1.lowercased() }
            let current = names.filter { !archived.contains($0) }, previous = names.filter { archived.contains($0) }
            let fromDose = doses.contains { $0.fromDose }, fromHealth = doses.contains { !$0.fromDose }
            let note = (fromDose && fromHealth ? tr("As marked in Dose and logged in Apple Health.")
                        : fromDose ? tr("As marked in Dose.") : tr("As logged in Apple Health."))
                + " " + tr("Not taken: doses that were due and never marked. Yet to take: due later today.")
            let colx = [M, M + 182, M + 232, M + 282, M + 334, M + 390, M + 452]
            let heads = [tr("Medication"), tr("Taken"), tr("Skipped"), tr("Not taken"), tr("Yet to take"),
                         tr("First in period"), tr("Last in period")]
            func head() {
                for (x, h) in zip(colx, heads) { t(h, x, y, 7.5, muted, bold: true, width: x > colx[0] ? 58 : nil) }
                y += 22
                hline(M, W - M, y - 3, axis, 0.6)
            }
            for (title, rows) in [(tr("Medication"), current), (tr("Previous medication"), previous)] where !rows.isEmpty {
                ensure(80)
                t(title, M, y, 12, ink, bold: true)
                y += 20
                if rows == current || current.isEmpty {
                    y += t(note, M, y, 8, muted, width: W - 2 * M).1 + 8
                }
                head()
                for name in rows {
                    let r = medRows[name]!
                    var label = name
                    if meds.first(where: { $0.name == name })?.asNeeded == true { label += " · " + tr("as needed") }
                    let h = t(label, colx[0], 0, 8.5, width: 172, measure: true).1
                    if y + h + 8 > bottom { newPage(); head() }
                    let yy = y
                    t(label, colx[0], yy, 8.5, ink, width: 172)
                    for (x, n) in zip(colx[1...4], [r.taken, r.skipped, r.missed, r.upcoming]) {
                        t(n > 0 ? "\(n)" : "–", x, yy, 8.5, n > 0 ? ink : muted)
                    }
                    t(Day.pretty(r.first, style: "d MMM yyyy"), colx[5], yy, 8.5, ink)
                    t(Day.pretty(r.last, style: "d MMM yyyy"), colx[6], yy, 8.5, ink)
                    y += max(h, 12) + 6
                }
                y += 10
            }
        }

        // ---- questionnaires (PHQ-9, GAD-7)
        if !surveys.isEmpty {
            ensure(90)
            t(tr("Questionnaires"), M, y, 12, ink, bold: true)
            y += 20
            y += t(tr("Answered in Sottovoce. Each answer counts 0 (not at all) to 3 (nearly every day); the score is their sum, out of 27 for PHQ-9 and 21 for GAD-7. The answers are listed in question order. Sottovoce gives no interpretation."),
                   M, y, 8, muted, width: W - 2 * M).1 + 8
            let colx = [M, M + 110, M + 180, M + 240, M + 370]
            let heads = [tr("Date"), tr("Questionnaire"), tr("Score"), tr("Answers"), tr("Difficulty")]
            func head() {
                for (x, h) in zip(colx, heads) { t(h, x, y, 7.5, muted, bold: true) }
                y += 14
                hline(M, W - M, y - 3, axis, 0.6)
            }
            head()
            let diffWords = SurveyText.current.difficultyOptions
            for s in surveys.sorted(by: { $0.date < $1.date }) {
                let diff = s.difficulty >= 0 ? diffWords[s.difficulty] : "–"
                let h = t(diff, colx[4], 0, 8.5, width: W - M - colx[4], measure: true).1
                if y + h + 8 > bottom { newPage(); head() }
                let yy = y
                t(Day.pretty(s.date, style: "d MMM yyyy"), colx[0], yy, 8.5, ink)
                t(s.name, colx[1], yy, 8.5, ink)
                t("\(s.score) / \(s.maxScore)", colx[2], yy, 8.5, ink, bold: true)
                t(s.answers.map(String.init).joined(separator: " "), colx[3], yy, 8.5, ink)
                t(diff, colx[4], yy, 8.5, ink, width: W - M - colx[4])
                y += max(h, 12) + 6
            }
            let credit = SurveyText.current.credit
            let h = t(credit, M, 0, 7, width: W - 2 * M, measure: true).1
            ensure(h + 6)
            t(credit, M, y + 2, 7, muted, width: W - 2 * M, italic: true)
            y += h + 16
        }

        // ---- dreams
        if includeDreams, let summary = store.dreamSummary(from: start, to: end) {
            ensure(70)
            t(tr("Dreams"), M, y, 12, ink, bold: true)
            y += 22
            y += t(summary, M, y, 9, ink2, width: W - 2 * M).1 + 18
        }

        // ---- day by day
        let showDreams = includeDreams && store.dreams.contains { $0.date >= start && $0.date <= end }
        let rows = days.filter { d in !store.entries(on: d).isEmpty || !store.events(on: d).isEmpty || sleep[d] != nil
            || (showDreams && !store.dreams(on: d).isEmpty) }
        if !rows.isEmpty {
            ensure(80)
            t(tr("Day by day"), M, y, 12, ink, bold: true)
            y += 22
            let hasSleep = !sleepDays.isEmpty
            var dcols: [(String, CGFloat)] = [(tr("Date"), 74), (tr("Mood"), 92), (tr("Energy"), 40), (tr("Anxiety"), 44)]
            if hasSleep { dcols.append((tr("Sleep"), 38)) }
            dcols.append((tr("Event"), 34))
            if showDreams { dcols.append((tr("Dream"), 52)) }
            if includeNotes || (showDreams && dreamText) {
                let heading = includeNotes && dreamText && showDreams ? tr("Notes and dreams") : includeNotes ? tr("Notes") : tr("Dreams")
                dcols.append((heading, W - 2 * M - dcols.map(\.1).reduce(0, +)))
            }
            var xs: [CGFloat] = []
            var x0 = M
            for c in dcols { xs.append(x0); x0 += c.1 }
            func head() {
                for (c, x) in zip(dcols, xs) { t(c.0, x, y, 7.5, muted, bold: true) }
                y += 14
                hline(M, W - M, y - 3, axis, 0.6)
            }
            head()
            func squash(_ s: String) -> String { s.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ") }
            for d in rows {
                let items = store.entries(on: d)
                var lines: [String] = []
                if includeNotes {
                    lines += items.filter { !$0.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !$0.hidden }
                        .map { "\($0.time)  \(squash($0.note))".trimmingCharacters(in: .whitespaces) }
                }
                let dayDreams = showDreams ? store.dreams(on: d) : []
                if dreamText {
                    lines += dayDreams.filter { !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !$0.hidden }
                        .map { tr("Dream: %@", squash($0.text)) }
                }
                let notes = lines.joined(separator: "\n")
                var h: CGFloat = 12
                if !notes.isEmpty { h = max(t(notes, xs.last!, 0, 7.5, width: dcols.last!.1 - 4, measure: true).1, 12) }
                if y + h + 6 > bottom { newPage(); head() }
                let yy = y
                if weekday(d) >= 6 { rect(CGRect(x: M, y: yy - 2, width: W - 2 * M, height: h + 4), tile) }
                t(Day.pretty(d, style: "EEE d MMM"), xs[0], yy, 8, ink)
                if let a = store.average(on: d) {
                    rect(CGRect(x: xs[1], y: yy + 1.5, width: 7, height: 7), moods[Mood.level(a)]!)
                    let count = items.filter { $0.mood > 0 }.count
                    t("\(Mood.name(Mood.level(a))) \(f1(a))" + (count > 1 ? " (\(count))" : ""), xs[1] + 11, yy, 8, ink)
                }
                for (k, e) in [true, false].enumerated() {
                    let v = store.scaleAverage(on: d, energy: e)
                    t(v.map { f1($0).replacingOccurrences(of: ".0", with: "") } ?? "", xs[2 + k], yy, 8, ink)
                }
                var col = 4
                if hasSleep { t(sleep[d].map(f1) ?? "", xs[col], yy, 8, ink); col += 1 }
                t(evs.filter { $0.date == d }.map { "\(number[$0.id] ?? 0)" }.joined(separator: ", "), xs[col], yy, 8, ink, bold: true)
                col += 1
                if showDreams && !dayDreams.isEmpty {
                    let mark: String
                    if dayDreams.contains(where: { $0.isNightmare }) { mark = tr("Nightmare") }
                    else if dayDreams.contains(where: { $0.feeling == "pleasant" }) { mark = tr("Pleasant") }
                    else if dayDreams.allSatisfy({ $0.tags.contains("forgotten") && $0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) { mark = tr("Forgotten") }
                    else { mark = tr("Yes") }
                    t(mark, xs[col], yy, 7.5, ink, bold: mark == tr("Nightmare"))
                }
                if !notes.isEmpty { t(notes, xs.last!, yy, 7.5, ink2, width: dcols.last!.1 - 4) }
                y += h + 5
            }
            y += 6
        }
    }
}


enum ReportPeriod: String, CaseIterable, Identifiable {
    case week, twoWeeks, month, threeMonths, sixMonths, year, all, custom
    var id: String { rawValue }

    var label: String {
        switch self {
        case .week: return tr("Last 7 days")
        case .twoWeeks: return tr("Last 2 weeks")
        case .month: return tr("Last 30 days")
        case .threeMonths: return tr("Last 3 months")
        case .sixMonths: return tr("Last 6 months")
        case .year: return tr("This year")
        case .all: return tr("Everything")
        case .custom: return tr("Choose the dates")
        }
    }

    @MainActor
    func range(_ store: AppStore, today: String = Day.today) -> (String, String) {
        switch self {
        case .week: return (Day.adding(-6, to: today), today)
        case .twoWeeks: return (Day.adding(-13, to: today), today)
        case .custom, .month: return (Day.adding(-29, to: today), today)
        case .threeMonths: return (Day.adding(-90, to: today), today)
        case .sixMonths: return (Day.adding(-181, to: today), today)
        case .year: return (String(today.prefix(4)) + "-01-01", today)
        case .all:
            let first = [store.entries.map(\.date).min(), store.events.map(\.date).min(), store.dreams.map(\.date).min()]
                .compactMap { $0 }.min() ?? today
            return (min(first, today), today)
        }
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xff) / 255, green: CGFloat((hex >> 8) & 0xff) / 255,
                  blue: CGFloat(hex & 0xff) / 255, alpha: 1)
    }
}
