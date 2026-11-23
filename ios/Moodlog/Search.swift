import Foundation

/// The search, the same as on the PC: the words in notes, the feeling words and events, ignoring
/// capitals and accents ("perche" finds "Perché"). With no words, every note, newest first.
enum Search {
    enum Hit: Identifiable, Hashable {
        case entry(Entry)
        case event(MoodEvent)

        var id: String {
            switch self {
            case .entry(let e): return "entry:" + e.id
            case .event(let e): return "event:" + e.id
            }
        }

        var date: String {
            switch self {
            case .entry(let e): return e.date
            case .event(let e): return e.date
            }
        }

        /// Newest first; an event comes before the moods of its day, like on the PC.
        fileprivate var order: String {
            switch self {
            case .entry(let e): return e.date + " " + (e.time.isEmpty ? "00:00" : e.time)
            case .event(let e): return e.date + " 99:99"
            }
        }
    }

    static let options: String.CompareOptions = [.caseInsensitive, .diacriticInsensitive]

    static func matches(_ text: String, _ query: String) -> Bool {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return !q.isEmpty && text.range(of: q, options: options) != nil
    }

    @MainActor
    static func results(_ store: AppStore, _ query: String) -> [Hit] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        var hits: [Hit] = []
        for e in store.entries {
            if q.isEmpty {
                if !e.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { hits.append(.entry(e)) }
            } else if matches(e.note, q) || matches(Feeling.text(e.feelings ?? []), q) {
                hits.append(.entry(e))
            }
        }
        if !q.isEmpty {
            for ev in store.events where matches(ev.title, q) || matches(ev.note, q) {
                hits.append(.event(ev))
            }
        }
        return hits.sorted { $0.order > $1.order }
    }

    /// The text with every match in bold.
    static func marked(_ text: String, _ query: String) -> AttributedString {
        var attr = AttributedString(text)
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return attr }
        var start = attr.startIndex
        while start < attr.endIndex, let r = attr[start...].range(of: q, options: options) {
            attr[r].inlinePresentationIntent = .stronglyEmphasized
            start = r.upperBound
        }
        return attr
    }
}
