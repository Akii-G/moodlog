import Foundation
import Observation

/// All entries and events on this iPhone, saved as one JSON file.
/// Merging follows the same rules as Moodlog on the PC: for each entry the newest change
/// wins, a deletion wins over changes made before it, and identical entries created
/// separately on two devices are not duplicated.
@MainActor
@Observable
final class AppStore {
    var entries: [Entry] = []
    var events: [MoodEvent] = []
    var deleted: [Tombstone] = []

    @ObservationIgnored var onChange: (@MainActor () -> Void)?
    @ObservationIgnored private let fileURL: URL

    private struct Saved: Codable {
        var entries: [Entry]
        var events: [MoodEvent]
        var deleted: [Tombstone]
    }

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("moodlog.json")
        load()
    }

    func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let saved = try? JSONDecoder().decode(Saved.self, from: data) else { return }
        entries = saved.entries
        events = saved.events
        deleted = saved.deleted
    }

    func save() {
        let saved = Saved(entries: entries, events: events, deleted: deleted)
        if let data = try? JSONEncoder().encode(saved) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }

    private func changed() {
        save()
        onChange?()
    }

    static func newID() -> String {
        let raw = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        return String(raw.prefix(16))
    }

    // MARK: - Reading

    func entries(on day: String) -> [Entry] {
        return entries.filter { $0.date == day }.sorted { $0.time < $1.time }
    }

    func moods(on day: String) -> [Int] {
        return entries(on: day).map { $0.mood }.filter { $0 > 0 }
    }

    func average(on day: String) -> Double? {
        let values = moods(on: day)
        if values.isEmpty { return nil }
        return Double(values.reduce(0, +)) / Double(values.count)
    }

    func scaleAverage(on day: String, energy: Bool) -> Double? {
        let values = entries(on: day).map { energy ? $0.energy : $0.anxiety }.filter { $0 > 0 }
        if values.isEmpty { return nil }
        return Double(values.reduce(0, +)) / Double(values.count)
    }

    func events(on day: String) -> [MoodEvent] {
        return events.filter { $0.date == day }
    }

    func hasNote(on day: String) -> Bool {
        return entries(on: day).contains { !$0.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    /// Days in a row with a mood, up to today (or yesterday if today is still empty).
    var streak: Int {
        var day = Day.today
        if average(on: day) == nil { day = Day.adding(-1, to: day) }
        var count = 0
        while average(on: day) != nil {
            count += 1
            day = Day.adding(-1, to: day)
        }
        return count
    }

    /// Average mood in the 14 days before an event and from the event day on.
    func moodAround(_ day: String, span: Int = 14) -> (before: (Double, Int), after: (Double, Int), complete: Bool) {
        func window(_ from: String, _ to: String) -> (Double, Int) {
            var values: [Double] = []
            var d = from
            while d <= to {
                if let a = average(on: d) { values.append(a) }
                d = Day.adding(1, to: d)
            }
            return values.isEmpty ? (0, 0) : (values.reduce(0, +) / Double(values.count), values.count)
        }
        let before = window(Day.adding(-span, to: day), Day.adding(-1, to: day))
        let afterEnd = Day.adding(span - 1, to: day)
        let after = window(day, min(afterEnd, Day.today))
        return (before, after, afterEnd <= Day.today)
    }

    // MARK: - Changing

    func addEntry(date: String, time: String, mood: Int, energy: Int, anxiety: Int, note: String) {
        entries.append(Entry(id: AppStore.newID(), date: date, time: time, mood: mood, energy: energy,
                             anxiety: anxiety, note: note, updated: Stamp.now()))
        changed()
    }

    func update(_ entry: Entry) {
        var copy = entry
        copy.updated = Stamp.now()
        if let i = entries.firstIndex(where: { $0.id == copy.id }) {
            entries[i] = copy
        }
        changed()
    }

    func delete(_ entry: Entry) {
        entries.removeAll { $0.id == entry.id }
        deleted.append(Tombstone(id: entry.id, type: "entry", deleted: Stamp.now(), date: entry.date))
        changed()
    }

    func addEvent(date: String, kind: String, title: String, note: String) {
        events.append(MoodEvent(id: AppStore.newID(), date: date, kind: kind, title: title, note: note,
                                updated: Stamp.now()))
        changed()
    }

    func update(_ event: MoodEvent) {
        var copy = event
        copy.updated = Stamp.now()
        if let i = events.firstIndex(where: { $0.id == copy.id }) {
            events[i] = copy
        }
        changed()
    }

    func delete(_ event: MoodEvent) {
        events.removeAll { $0.id == event.id }
        deleted.append(Tombstone(id: event.id, type: "event", deleted: Stamp.now(), date: event.date))
        changed()
    }

    // MARK: - Sync

    func state() -> SyncState {
        return SyncState(entries: entries, events: events, deleted: deleted, changed: nil)
    }

    /// Merge what the PC sent. Returns how many things changed here.
    @discardableResult
    func absorb(_ other: SyncState) -> Int {
        var changes = 0
        var tombs: [String: Tombstone] = [:]
        for t in deleted + other.deleted {
            if let old = tombs[t.id], old.deleted >= t.deleted { continue }
            tombs[t.id] = t
        }

        var byID: [String: Entry] = [:]
        for e in entries {
            if let old = byID[e.id], old.updated >= e.updated { continue }
            byID[e.id] = e
        }
        for e in other.entries {
            if let t = tombs[e.id], t.deleted >= e.updated { continue }
            if let mine = byID[e.id] {
                if e.updated > mine.updated {
                    byID[e.id] = e
                    changes += 1
                }
            } else if byID.values.contains(where: {
                $0.date == e.date && $0.time == e.time && $0.mood == e.mood && $0.energy == e.energy
                    && $0.anxiety == e.anxiety && $0.note == e.note
            }) {
                continue
            } else {
                byID[e.id] = e
                changes += 1
            }
        }
        for t in tombs.values where t.type == "entry" {
            if let mine = byID[t.id], mine.updated <= t.deleted {
                byID.removeValue(forKey: t.id)
                changes += 1
            }
        }

        var eventsByID: [String: MoodEvent] = [:]
        for e in events {
            if let old = eventsByID[e.id], old.updated >= e.updated { continue }
            eventsByID[e.id] = e
        }
        for e in other.events {
            if let t = tombs[e.id], t.deleted >= e.updated { continue }
            if let mine = eventsByID[e.id] {
                if e.updated > mine.updated {
                    eventsByID[e.id] = e
                    changes += 1
                }
            } else if eventsByID.values.contains(where: {
                $0.date == e.date && $0.kind == e.kind && $0.title == e.title && $0.note == e.note
            }) {
                continue
            } else {
                eventsByID[e.id] = e
                changes += 1
            }
        }
        for t in tombs.values where t.type == "event" {
            if let mine = eventsByID[t.id], mine.updated <= t.deleted {
                eventsByID.removeValue(forKey: t.id)
                changes += 1
            }
        }

        entries = Array(byID.values)
        events = Array(eventsByID.values).sorted { $0.date < $1.date }
        deleted = Array(tombs.values)
        save()
        return changes
    }
}
