import UIKit

/// Your diary as a book: the notes month by month, each day with its mood faces, feeling words,
/// activities and events, as an A4 PDF to keep or print. The same pages as Sottovoce on the PC.
/// Moments with only a face and nothing written or tagged are left out, so it reads as a diary.
@MainActor
final class Book {
    static let W: CGFloat = 595, H: CGFloat = 842, M: CGFloat = 42
    private let ink = UIColor(hex: 0x1b1b1b), ink2 = UIColor(hex: 0x4a4945), muted = UIColor(hex: 0x6d6b66)
    private let grid = UIColor(hex: 0xe3e2dc)
    private let moods: [Int: UIColor] = [1: UIColor(hex: 0xcf3f3f), 2: UIColor(hex: 0xeb9a94), 3: UIColor(hex: 0xbdbbb3),
                                         4: UIColor(hex: 0x8fbcf0), 5: UIColor(hex: 0x2a78d6)]

    struct Page { let day: String; let entries: [Entry]; let events: [MoodEvent] }

    private let store: AppStore
    let pages: [Page]
    private var ctx: UIGraphicsPDFRendererContext!
    private var pageNo = 0
    private var y: CGFloat = 42

    init(store: AppStore, start: String?, end: String, includePrivate: Bool) {
        self.store = store
        let days = Set(store.entries.map(\.date)).union(store.events.map(\.date))
            .filter { (start == nil || $0 >= start!) && $0 <= end }.sorted()
        pages = days.compactMap { day in
            let entries = store.entries(on: day).filter { e in
                (includePrivate || !e.hidden)
                    && (!e.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        || !(e.feelings ?? []).isEmpty || !(e.activities ?? []).isEmpty)
            }
            let events = store.events.filter { $0.date == day }
            return entries.isEmpty && events.isEmpty ? nil : Page(day: day, entries: entries, events: events)
        }
        moodsByDay = Dictionary(grouping: store.entries.filter { $0.mood > 0 && (includePrivate || !$0.hidden) }, by: \.date)
            .mapValues { $0.map(\.mood) }
    }

    private let moodsByDay: [String: [Int]]

    /// Start of a period for the book; nil means from the first day.
    static func start(_ period: BookPeriod, today: String = Day.today) -> String? {
        switch period {
        case .all: return nil
        case .year: return String(today.prefix(4)) + "-01-01"
        case .twelveMonths: return Day.adding(-364, to: today)
        case .threeMonths: return Day.adding(-90, to: today)
        }
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
    private func text(_ s: String, _ x: CGFloat, _ top: CGFloat, _ size: CGFloat, _ color: UIColor,
                      bold: Bool = false, width: CGFloat? = nil, align: NSTextAlignment = .left, italic: Bool = false) -> CGFloat {
        let w = width ?? (Book.W - Book.M - x)
        let a = NSAttributedString(string: s, attributes: attrs(size, color, bold: bold, italic: italic, align: align))
        let r = a.boundingRect(with: CGSize(width: w, height: 2000), options: [.usesLineFragmentOrigin], context: nil)
        a.draw(with: CGRect(x: x, y: top, width: w, height: ceil(r.height)), options: [.usesLineFragmentOrigin], context: nil)
        return ceil(r.height)
    }

    private func measure(_ s: String, _ size: CGFloat, _ width: CGFloat, italic: Bool = false) -> CGFloat {
        let a = NSAttributedString(string: s, attributes: attrs(size, ink, italic: italic))
        return ceil(a.boundingRect(with: CGSize(width: width, height: 2000), options: [.usesLineFragmentOrigin], context: nil).height)
    }

    private func footer() {
        if pageNo > 1 {
            text("\(pageNo)", Book.M, Book.H - 28, 8, muted, width: Book.W - 2 * Book.M, align: .center)
        }
    }

    private func newPage() {
        if pageNo > 0 { footer() }
        ctx.beginPage()
        pageNo += 1
        y = Book.M
    }

    private func ensure(_ h: CGFloat) { if y + h > Book.H - 60 { newPage() } }

    /// The same line-drawn face as everywhere in Sottovoce, in the mood's colour.
    private func face(_ c: CGPoint, _ r: CGFloat, _ mood: Int) {
        let color = moods[mood] ?? muted
        color.setStroke(); color.setFill()
        let lw = max(1.2, r * 0.13)
        let ring = UIBezierPath(ovalIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
        ring.lineWidth = lw; ring.stroke()
        let ex = r * 0.36, ey = r * 0.20, er = max(0.9, r * 0.11)
        for sx: CGFloat in [-1, 1] {
            UIBezierPath(ovalIn: CGRect(x: c.x + sx * ex - er, y: c.y - ey - er, width: 2 * er, height: 2 * er)).fill()
        }
        let w = r * 0.46, my = c.y + r * 0.30
        let p = UIBezierPath()
        p.lineWidth = lw; p.lineCapStyle = .round; p.lineJoinStyle = .round
        switch mood {
        case 1:
            for sx: CGFloat in [-1, 1] {
                p.move(to: CGPoint(x: c.x + sx * (ex + r * 0.18), y: c.y - ey - r * 0.22))
                p.addLine(to: CGPoint(x: c.x + sx * (ex - r * 0.14), y: c.y - ey - r * 0.36))
            }
            p.move(to: CGPoint(x: c.x - w, y: my + r * 0.22))
            p.addCurve(to: CGPoint(x: c.x + w, y: my + r * 0.22), controlPoint1: CGPoint(x: c.x - w * 0.5, y: my - r * 0.26),
                       controlPoint2: CGPoint(x: c.x + w * 0.5, y: my - r * 0.26))
            p.stroke()
        case 2:
            p.move(to: CGPoint(x: c.x - w, y: my + r * 0.12))
            p.addCurve(to: CGPoint(x: c.x + w, y: my + r * 0.12), controlPoint1: CGPoint(x: c.x - w * 0.5, y: my - r * 0.12),
                       controlPoint2: CGPoint(x: c.x + w * 0.5, y: my - r * 0.12))
            p.stroke()
        case 3:
            p.move(to: CGPoint(x: c.x - w, y: my + r * 0.04))
            p.addLine(to: CGPoint(x: c.x + w, y: my + r * 0.04))
            p.stroke()
        case 4:
            p.move(to: CGPoint(x: c.x - w, y: my - r * 0.08))
            p.addCurve(to: CGPoint(x: c.x + w, y: my - r * 0.08), controlPoint1: CGPoint(x: c.x - w * 0.5, y: my + r * 0.24),
                       controlPoint2: CGPoint(x: c.x + w * 0.5, y: my + r * 0.24))
            p.stroke()
        default:
            p.move(to: CGPoint(x: c.x - w * 1.1, y: my - r * 0.14))
            p.addLine(to: CGPoint(x: c.x + w * 1.1, y: my - r * 0.14))
            p.addCurve(to: CGPoint(x: c.x - w * 1.1, y: my - r * 0.14), controlPoint1: CGPoint(x: c.x + w * 0.8, y: my + r * 0.44),
                       controlPoint2: CGPoint(x: c.x - w * 0.8, y: my + r * 0.44))
            p.close(); p.fill(); p.stroke()
        }
    }

    // MARK: the book

    func write(to url: URL) throws {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: Book.W, height: Book.H))
        try renderer.writePDF(to: url) { context in
            self.ctx = context
            self.drawAll()
            self.footer()
        }
    }

    private func drawAll() {
        let M = Book.M, W = Book.W, inner = Book.W - 2 * Book.M - 16
        guard let first = pages.first?.day, let last = pages.last?.day else { return }
        newPage()
        y = 300
        y += text(tr("My diary"), M, y, 30, ink, bold: true, width: W - 2 * M, align: .center) + 18
        let a = Day.pretty(first, style: "LLLL yyyy"), b = Day.pretty(last, style: "LLLL yyyy")
        y += text(a == b ? a : "\(a) – \(b)", M, y, 13, ink2, width: W - 2 * M, align: .center) + 8
        text(pages.count == 1 ? tr("1 day") : tr("%@ days", "\(pages.count)"), M, y, 10, muted, width: W - 2 * M, align: .center)
        text(tr("Written sottovoce, made with Sottovoce"), M, Book.H - 120, 9, muted, width: W - 2 * M, align: .center)
        newPage()

        var month = ""
        for page in pages {
            if page.day.prefix(7) != month {
                if !month.isEmpty { newPage() }
                month = String(page.day.prefix(7))
                y += text(Day.pretty(page.day, style: "LLLL yyyy"), M, y, 22, ink, bold: true) + 10
                grid.setFill(); UIRectFill(CGRect(x: M, y: y, width: W - 2 * M, height: 0.8))
                y += 16
            }
            ensure(60)
            if let ms = moodsByDay[page.day], !ms.isEmpty {
                let avg = Double(ms.reduce(0, +)) / Double(ms.count)
                (moods[Mood.level(avg)] ?? muted).setFill()
                UIBezierPath(ovalIn: CGRect(x: M, y: y + 3, width: 10, height: 10)).fill()
            }
            y += text(Day.pretty(page.day, style: "EEEE d MMMM"), M + 16, y, 12, ink, bold: true) + 8
            for ev in page.events {
                let line = "\(EventKind.label(for: ev.kind)) · \(ev.title)" + (ev.note.isEmpty ? "" : " — \(ev.note)")
                ensure(measure(line, 9.5, inner, italic: true) + 6)
                y += text(line, M + 16, y, 9.5, ink2, width: inner, italic: true) + 6
            }
            for e in page.entries {
                let head = [e.time, e.mood > 0 ? Mood.name(e.mood) : ""].filter { !$0.isEmpty }.joined(separator: " · ")
                if !head.isEmpty {
                    ensure(18)
                    var x = M + 16
                    if e.mood > 0 { face(CGPoint(x: M + 22, y: y + 6.5), 6, e.mood); x = M + 34 }
                    text(head, x, y + 0.5, 8.5, muted)
                    y += 17
                }
                for para in e.note.split(separator: "\n").map({ $0.trimmingCharacters(in: .whitespaces) }) where !para.isEmpty {
                    ensure(measure(para, 10.5, inner) + 4)
                    y += text(para, M + 16, y, 10.5, ink, width: inner) + 4
                }
                let words = [Feeling.text(e.feelings ?? []), Activity.text(e.activities ?? [])].filter { !$0.isEmpty }
                    .joined(separator: " · ")
                if !words.isEmpty {
                    ensure(measure(words, 8.5, inner) + 4)
                    y += text(words, M + 16, y, 8.5, muted, width: inner) + 4
                }
                y += 8
            }
            y += 10
        }
    }
}

enum BookPeriod: String, CaseIterable, Identifiable {
    case all, year, twelveMonths, threeMonths
    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: return tr("All of it")
        case .year: return tr("This year")
        case .twelveMonths: return tr("The last 12 months")
        case .threeMonths: return tr("The last 3 months")
        }
    }
}
