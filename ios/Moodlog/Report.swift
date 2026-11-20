import UIKit

/// The report for your doctor: the same sections as Moodlog on the PC and Android — a summary,
/// how often each mood was logged, mood / energy and anxiety / sleep over the period, events
/// with the mood before and after, dreams, and a day-by-day table. A4, printable.
@MainActor
final class Report {
    static let W: CGFloat = 595, H: CGFloat = 842, M: CGFloat = 42
    private let ink = UIColor(hex: 0x1b1b1b), ink2 = UIColor(hex: 0x4a4945), muted = UIColor(hex: 0x6d6b66)
    private let grid = UIColor(hex: 0xe3e2dc), tile = UIColor(hex: 0xf4f3ef), health = UIColor(hex: 0x1baf7a)
    private let energy = UIColor(hex: 0xb87c10), anxiety = UIColor(hex: 0x7b5ad6)
    private let moods: [Int: UIColor] = [1: UIColor(hex: 0xcf3f3f), 2: UIColor(hex: 0xeb9a94), 3: UIColor(hex: 0xbdbbb3),
                                         4: UIColor(hex: 0x8fbcf0), 5: UIColor(hex: 0x2a78d6)]

    private let store: AppStore
    private let start: String, end: String
    private let includeNotes: Bool, sleep: [String: Double], includeDreams: Bool, dreamText: Bool
    private let days: [String]
    private var ctx: UIGraphicsPDFRendererContext!
    private var pageNo = 0
    private var y: CGFloat = 42
    private var weekly: Bool { days.count > 92 }

    init(store: AppStore, start: String, end: String, includeNotes: Bool, sleep: [String: Double],
         includeDreams: Bool, dreamText: Bool) {
        self.store = store; self.start = start; self.end = end; self.includeNotes = includeNotes
        self.sleep = sleep; self.includeDreams = includeDreams; self.dreamText = dreamText
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
        text(tr("Made with Moodlog · self-reported data, not a medical record"), Report.M, Report.H - 28, 7.5, muted)
        text(tr("Page %@", "\(pageNo)"), Report.M, Report.H - 28, 7.5, muted, width: Report.W - 2 * Report.M, align: .right)
    }

    private func newPage() {
        if pageNo > 0 { footer() }
        ctx.beginPage()
        pageNo += 1
        y = Report.M
    }

    private func ensure(_ h: CGFloat) { if y + h > Report.H - 48 { newPage() } }

    private func heading(_ s: String) {
        ensure(40); y += 8
        y += text(s, Report.M, y, 12, ink, bold: true) + 6
    }

    // MARK: the report

    func write(to url: URL) throws {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: Report.W, height: Report.H))
        try renderer.writePDF(to: url) { context in
            self.ctx = context
            self.drawAll()
            self.footer()
        }
    }

    private func drawAll() {
        let M = Report.M, W = Report.W
        newPage()
        let moodDays = days.filter { store.average(on: $0) != nil }
        let entries = days.flatMap { store.entries(on: $0) }
        y += text(tr("Mood report"), M, y, 20, ink, bold: true) + 2
        y += text(tr("%@ – %@ · %@ days · made with Moodlog on %@", Day.pretty(start, style: "d MMM yyyy"),
                     Day.pretty(end, style: "d MMM yyyy"), "\(days.count)", Day.pretty(Day.today, style: "d MMM yyyy")),
                  M, y, 9, muted) + 14

        let avgs = moodDays.compactMap { store.average(on: $0) }
        var tiles: [(String, String)] = [
            (tr("Days with a mood"), tr("%@ of %@", "\(moodDays.count)", "\(days.count)")),
            (tr("Average mood (1–5)"), avgs.isEmpty ? "–" : "\(one(mean(avgs)))  \(Mood.name(Mood.level(mean(avgs))))"),
            (tr("Moods logged"), "\(entries.filter { $0.mood > 0 }.count)"),
        ]
        let en = entries.map(\.energy).filter { $0 > 0 }.map(Double.init)
        let an = entries.map(\.anxiety).filter { $0 > 0 }.map(Double.init)
        if !en.isEmpty { tiles.append((tr("Average energy (1–5)"), one(mean(en)))) }
        if !an.isEmpty { tiles.append((tr("Average anxiety (1–5)"), one(mean(an)))) }
        if !sleep.isEmpty { tiles.append((tr("Average sleep"), tr("%@ h (%@ nights)", one(mean(Array(sleep.values))), "\(sleep.count)"))) }
        let tw = (W - 2 * M - 16) / 3
        for row in stride(from: 0, to: tiles.count, by: 3) {
            for (i, t) in tiles[row..<min(row + 3, tiles.count)].enumerated() {
                let x = M + CGFloat(i) * (tw + 8)
                fill(CGRect(x: x, y: y, width: tw, height: 46), tile, radius: 6)
                text(t.0, x + 8, y + 6, 7.5, muted, width: tw - 16)
                text(t.1, x + 8, y + 20, 13, ink, bold: true, width: tw - 16)
            }
            y += 54
        }
        if entries.contains(where: { $0.mood > 0 }) { countBars(entries) }
        if !moodDays.isEmpty { moodChart() }
        if !en.isEmpty || !an.isEmpty { scaleChart() }
        if !sleep.isEmpty { sleepChart() }
        eventsTable()
        if includeDreams { dreamsSection() }
        dayTable()
    }

    private func mean(_ v: [Double]) -> Double { v.reduce(0, +) / Double(max(v.count, 1)) }
    private func one(_ v: Double) -> String { String(format: "%.1f", v) }

    private func countBars(_ entries: [Entry]) {
        let M = Report.M
        ensure(110)
        y += text(tr("How often each mood was logged"), M, y, 10, ink, bold: true) + 6
        let counts = Dictionary(uniqueKeysWithValues: (1...5).map { l in (l, entries.filter { $0.mood == l }.count) })
        let most = CGFloat(max(counts.values.max() ?? 1, 1))
        let labelW: CGFloat = 70, barW = Report.W - 2 * M - labelW - 40
        for level in stride(from: 5, through: 1, by: -1) {
            let n = CGFloat(counts[level] ?? 0)
            text(Mood.name(level), M, y + 1, 8.5, ink2, width: labelW - 6)
            fill(CGRect(x: M + labelW, y: y + 2, width: barW * n / most, height: 10), moods[level]!, radius: 2)
            text("\(Int(n))", M + labelW + barW * n / most + 5, y + 1, 8.5, muted, width: 40)
            y += 15
        }
        y += 8
    }

    private func series(_ value: (String) -> Double?) -> ([(Int, Double)], Int) {
        if !weekly { return (days.enumerated().compactMap { i, d in value(d).map { (i, $0) } }, days.count) }
        let weeks = stride(from: 0, to: days.count, by: 7).map { Array(days[$0..<min($0 + 7, days.count)]) }
        return (weeks.enumerated().compactMap { i, w in
            let v = w.compactMap(value); return v.isEmpty ? nil : (i, mean(v)) }, weeks.count)
    }

    private func frame(_ title: String, height: CGFloat, labels: [String], note: String? = nil,
                       legend: [(String, UIColor)] = [], min lo: Double = 1, max hi: Double = 5) -> (CGFloat, CGFloat, (Double) -> CGFloat) {
        let M = Report.M, W = Report.W
        ensure(height + 60)
        y += text(title, M, y, 10, ink, bold: true) + 4
        if !legend.isEmpty {
            var x = M
            for (name, color) in legend {
                dot(CGPoint(x: x + 4, y: y + 6), 3.5, color)
                x += 12
                text(name, x, y, 8, ink2, width: 120)
                x += NSAttributedString(string: name, attributes: attrs(8, ink)).size().width + 16
            }
            y += 14
        }
        let left = M + 52, top = y + 4
        let yOf = { (v: Double) -> CGFloat in top + height - CGFloat((v - lo) / (hi - lo)) * height }
        for (i, l) in labels.enumerated() {
            let v = lo + Double(i) * (hi - lo) / Double(labels.count - 1)
            line(CGPoint(x: left, y: yOf(v)), CGPoint(x: W - M, y: yOf(v)), grid, 0.6)
            text(l, M, yOf(v) - 5, 7.5, muted, width: 48)
        }
        y = top + height + 4
        text(weekly ? tr("weeks") : Day.pretty(start, style: "d MMM"), left, y, 7, muted, width: 120)
        text(Day.pretty(end, style: "d MMM"), left, y, 7, muted, width: W - M - left, align: .right)
        y += 12
        if let note { y += text(note, M, y, 7.5, muted, italic: true) + 4 }
        y += 8
        return (left, top, yOf)
    }

    private func xOf(_ i: Int, _ n: Int, _ left: CGFloat) -> CGFloat {
        n <= 1 ? (left + Report.W - Report.M) / 2 : left + CGFloat(i) * (Report.W - Report.M - left) / CGFloat(n - 1)
    }

    private func drawLine(_ points: [(Int, Double)], _ n: Int, _ left: CGFloat, _ yOf: (Double) -> CGFloat,
                          _ color: UIColor, dot dotColor: (Double) -> UIColor) {
        let p = UIBezierPath()
        for (k, pt) in points.enumerated() {
            let c = CGPoint(x: xOf(pt.0, n, left), y: yOf(pt.1))
            if k == 0 { p.move(to: c) } else { p.addLine(to: c) }
        }
        p.lineWidth = 1.2; p.lineJoinStyle = .round
        color.setStroke(); p.stroke()
        for pt in points {
            let c = CGPoint(x: xOf(pt.0, n, left), y: yOf(pt.1))
            dot(c, 3.2, .white); dot(c, 2.4, dotColor(pt.1))
        }
    }

    private func moodChart() {
        let (points, n) = series { self.store.average(on: $0) }
        let evs = store.events.filter { $0.date >= start && $0.date <= end }.sorted { $0.date < $1.date }
        let (left, top, yOf) = frame(tr("Mood, %@", weekly ? tr("weekly average") : tr("daily average")), height: 118,
                                     labels: (1...5).map { Mood.name($0) },
                                     note: evs.isEmpty ? nil : tr("Numbered marks are the events listed below."))
        for (k, e) in evs.enumerated() {
            let idx = days.firstIndex(of: e.date) ?? 0
            let x = xOf(weekly ? idx / 7 : idx, n, left)
            line(CGPoint(x: x, y: top), CGPoint(x: x, y: yOf(1)), muted, 0.7, dashed: true)
            text("\(k + 1)", x - 10, top - 13, 7, ink2, bold: true, width: 20, align: .center)
        }
        drawLine(points, n, left, yOf, UIColor(hex: 0x9a9890)) { self.moods[Mood.level($0)]! }
    }

    private func scaleChart() {
        let (e, n) = series { self.store.scaleAverage(on: $0, energy: true) }
        let (a, _) = series { self.store.scaleAverage(on: $0, energy: false) }
        let (left, _, yOf) = frame(tr("Energy and anxiety (1–5)"), height: 96, labels: (1...5).map { "\($0)" },
                                   note: tr("Energy: 1 very low, 5 very high. Anxiety: 1 calm, 5 very anxious."),
                                   legend: [(tr("Energy"), energy), (tr("Anxiety"), anxiety)])
        drawLine(e, n, left, yOf, energy) { _ in self.energy }
        drawLine(a, n, left, yOf, anxiety) { _ in self.anxiety }
    }

    private func sleepChart() {
        let (points, n) = series { self.sleep[$0] }
        let top = (max(points.map { $0.1 }.max() ?? 8, 9) / 3).rounded(.up) * 3
        let (left, _, yOf) = frame(tr("Sleep (hours a night%@)", weekly ? tr(", weekly average") : ""), height: 90,
                                   labels: (0...3).map { "\(Int(Double($0) * top / 3)) h" }, min: 0, max: top)
        let bw = min(max((Report.W - Report.M - left) / CGFloat(max(n, 1)) * 0.7, 1.5), 10)
        for (i, v) in points {
            let x = xOf(i, n, left)
            fill(CGRect(x: x - bw / 2, y: yOf(v), width: bw, height: yOf(0) - yOf(v)), health)
        }
    }

    private func window(_ w: (Double, Int)) -> String { w.1 < 3 ? "–" : "\(one(w.0)) (\(w.1))" }

    private func eventsTable() {
        let M = Report.M, W = Report.W
        let evs = store.events.filter { $0.date >= start && $0.date <= end }.sorted { $0.date < $1.date }
        if evs.isEmpty { return }
        heading(tr("Events"))
        var widths: [CGFloat] = [18, 66, 74, 0, 70, 70]
        widths[3] = W - 2 * M - widths.reduce(0, +)
        let heads = ["#", tr("Date"), tr("Type"), tr("What happened"), tr("Mood 2w before"), tr("Mood 2w after")]
        func head() {
            var x = M
            for (i, h) in heads.enumerated() { text(h, x, y, 7.5, muted, bold: true, width: widths[i] - 4); x += widths[i] }
            y += 14
            line(CGPoint(x: M, y: y - 3), CGPoint(x: W - M, y: y - 3), grid, 0.6)
        }
        head()
        var anyOpen = false
        for (k, e) in evs.enumerated() {
            let around = store.moodAround(e.date)
            if !around.complete { anyOpen = true }
            let what = e.note.isEmpty ? e.title : "\(e.title)\n\(e.note)"
            let cells = ["\(k + 1)", Day.pretty(e.date, style: "d MMM yyyy"), EventKind.label(for: e.kind), what,
                         window(around.before), window(around.after) + (!around.complete && around.after.1 >= 3 ? " *" : "")]
            let rowH = max(cells.indices.map { measure(cells[$0], 8.5, widths[$0] - 4) }.max() ?? 12, 12) + 6
            if y + rowH > Report.H - 48 { newPage(); head() }
            var x = M
            for (i, s) in cells.enumerated() { text(s, x, y, 8.5, i == 0 ? muted : ink, width: widths[i] - 4); x += widths[i] }
            y += rowH
        }
        if anyOpen { y += text("* " + tr("so far"), M, y, 7, muted, italic: true) }
        y += 6
    }

    private func dreamsSection() {
        let M = Report.M, W = Report.W
        let ds = store.dreams.filter { $0.date >= start && $0.date <= end }.sorted { $0.date < $1.date }
        if ds.isEmpty { return }
        heading(tr("Dreams"))
        if let summary = store.dreamSummary(from: start, to: end) { y += text(summary, M, y, 9, ink2) + 6 }
        guard dreamText else { return }
        for d in ds {
            let head = [Day.pretty(d.date, style: "d MMM yyyy"), d.details].filter { !$0.isEmpty }.joined(separator: " · ")
            ensure(measure(head, 8, W - 2 * M, bold: true) + (d.text.isEmpty ? 0 : measure(d.text, 8.5, W - 2 * M)) + 6)
            y += text(head, M, y, 8, muted, bold: true)
            if !d.text.isEmpty { y += text(d.text, M, y, 8.5, ink) }
            y += 6
        }
    }

    private func dayTable() {
        let M = Report.M, W = Report.W
        heading(tr("Day by day"))
        var cols: [(String, CGFloat)] = [(tr("Date"), 70), (tr("Mood"), 96), (tr("Energy"), 40), (tr("Anxiety"), 44)]
        if !sleep.isEmpty { cols.append((tr("Sleep"), 38)) }
        cols.append((tr("Event"), 34))
        if includeDreams { cols.append((tr("Dream"), 40)) }
        if includeNotes { cols.append((tr("Notes"), W - 2 * M - cols.map { $0.1 }.reduce(0, +))) }
        func head() {
            var x = M
            for (n, w) in cols { text(n, x, y, 7.5, muted, bold: true, width: w - 4); x += w }
            y += 14
            line(CGPoint(x: M, y: y - 3), CGPoint(x: W - M, y: y - 3), grid, 0.6)
        }
        head()
        func avg(_ v: [Int]) -> String { let f = v.filter { $0 > 0 }; return f.isEmpty ? "" : one(mean(f.map(Double.init))) }
        for d in days.reversed() {
            let es = store.entries(on: d)
            let hasDream = includeDreams && !store.dreams(on: d).isEmpty
            if es.isEmpty && store.events(on: d).isEmpty && !hasDream && sleep[d] == nil { continue }
            let moodText = es.filter { $0.mood > 0 }.map { Mood.name($0.mood) }.joined(separator: ", ")
            var cells = [Day.pretty(d, style: "EEE d MMM"), moodText.isEmpty ? "–" : moodText,
                         avg(es.map(\.energy)), avg(es.map(\.anxiety))]
            if !sleep.isEmpty { cells.append(sleep[d].map(one) ?? "") }
            cells.append(store.events(on: d).isEmpty ? "" : "●")
            if includeDreams { cells.append(hasDream ? (store.dreams(on: d).contains { $0.isNightmare } ? tr("bad") : "●") : "") }
            if includeNotes {
                cells.append(es.filter { !$0.note.isEmpty }.map { ($0.time.isEmpty ? "" : $0.time + " ") + $0.note }.joined(separator: "\n"))
            }
            let rowH = max(cols.indices.map { measure(cells[$0], 8, cols[$0].1 - 4) }.max() ?? 10, 10) + 4
            if y + rowH > Report.H - 48 { newPage(); head() }
            var x = M
            for (i, (_, w)) in cols.enumerated() {
                if i == 1, let a = store.average(on: d) {
                    dot(CGPoint(x: x + 3, y: y + 5), 2.6, moods[Mood.level(a)]!)
                    text(cells[i], x + 9, y, 8, ink, width: w - 13)
                } else {
                    text(cells[i], x, y, 8, i == 0 ? ink2 : ink, width: w - 4)
                }
                x += w
            }
            y += rowH
            line(CGPoint(x: M, y: y - 2), CGPoint(x: W - M, y: y - 2), grid, 0.4)
        }
    }
}

enum ReportPeriod: String, CaseIterable, Identifiable {
    case month, threeMonths, sixMonths, year, all
    var id: String { rawValue }

    var label: String {
        switch self {
        case .month: return tr("Last 30 days")
        case .threeMonths: return tr("Last 3 months")
        case .sixMonths: return tr("Last 6 months")
        case .year: return tr("This year")
        case .all: return tr("Everything")
        }
    }

    @MainActor
    func range(_ store: AppStore, today: String = Day.today) -> (String, String) {
        switch self {
        case .month: return (Day.adding(-29, to: today), today)
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
