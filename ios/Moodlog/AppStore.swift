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
    var dreams: [Dream] = []
    var surveys: [Survey] = []
    var deleted: [Tombstone] = []

    @ObservationIgnored var onChange: (@MainActor () -> Void)?
    @ObservationIgnored private let fileURL: URL

    private struct Saved: Codable {
        var entries: [Entry]
        var events: [MoodEvent]
        var deleted: [Tombstone]
        var dreams: [Dream]?  // optional: files saved by 1.1 and older have none
        var surveys: [Survey]?  // optional: files saved by 1.7 and older have none
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
        dreams = saved.dreams ?? []
        surveys = saved.surveys ?? []
        deleted = saved.deleted
    }

    func save() {
        let saved = Saved(entries: entries, events: events, deleted: deleted, dreams: dreams, surveys: surveys)
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

    func dreams(on day: String) -> [Dream] {
        return dreams.filter { $0.date == day }
    }

    /// Dream counts for a period, and the mood on days after a disturbing dream vs other days.
    func dreamSummary(from start: String, to end: String) -> String? {
        let items = dreams.filter { $0.date >= start && $0.date <= end }
        let nights = Set(items.map { $0.date })
        if nights.isEmpty { return nil }
        let bad = Set(items.filter { $0.isNightmare }.map { $0.date })
        let pleasant = Set(items.filter { $0.feeling == "pleasant" }.map { $0.date })
        var parts = [trn("%@ night with a dream entry", "%@ nights with a dream entry", nights.count)]
        if !bad.isEmpty {
            parts.append(trn("%@ with a nightmare or a disturbing dream",
                             "%@ with a nightmare or a disturbing dream", bad.count))
        }
        if !pleasant.isEmpty { parts.append(trn("%@ pleasant one", "%@ pleasant ones", pleasant.count)) }
        var text = parts.joined(separator: ", ") + "."
        let afterBad = bad.compactMap { average(on: $0) }
        let afterOther = nights.subtracting(bad).compactMap { average(on: $0) }
        if afterBad.count >= 3 && afterOther.count >= 3 {
            let a = afterBad.reduce(0, +) / Double(afterBad.count)
            let b = afterOther.reduce(0, +) / Double(afterOther.count)
            text += " " + tr("Your mood on days after a disturbing dream: %@; after other dreams: %@. This shows a pattern in your data, not a cause.",
                             String(format: "%.1f", a), String(format: "%.1f", b))
        }
        return text
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

    func addEntry(date: String, time: String, mood: Int, energy: Int, anxiety: Int, note: String,
                  isPrivate: Bool = false, feelings: [String] = []) {
        entries.append(Entry(id: AppStore.newID(), date: date, time: time, mood: mood, energy: energy,
                             anxiety: anxiety, note: note, updated: Stamp.now(), isPrivate: isPrivate ? true : nil,
                             feelings: feelings.isEmpty ? nil : Feeling.clean(feelings)))
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

    func addDream(date: String, feeling: String, tags: [String], text: String, isPrivate: Bool = false) {
        dreams.append(Dream(id: AppStore.newID(), date: date, feeling: feeling, tags: tags, text: text,
                            updated: Stamp.now(), isPrivate: isPrivate ? true : nil))
        changed()
    }

    func update(_ dream: Dream) {
        var copy = dream
        copy.updated = Stamp.now()
        if let i = dreams.firstIndex(where: { $0.id == copy.id }) {
            dreams[i] = copy
        }
        changed()
    }

    func delete(_ dream: Dream) {
        dreams.removeAll { $0.id == dream.id }
        deleted.append(Tombstone(id: dream.id, type: "dream", deleted: Stamp.now(), date: dream.date))
        changed()
    }

    @discardableResult
    func addSurvey(kind: SurveyKind, answers: [Int], difficulty: Int, isPrivate: Bool = false) -> Survey {
        let now = Date()
        let survey = Survey(id: AppStore.newID(), date: Day.string(now), time: Day.timeString(now), kind: kind.rawValue,
                            answers: answers, difficulty: difficulty, updated: Stamp.now(), isPrivate: isPrivate ? true : nil)
        surveys.append(survey)
        surveys.sort { ($0.date, $0.time) < ($1.date, $1.time) }
        changed()
        return survey
    }

    func delete(_ survey: Survey) {
        surveys.removeAll { $0.id == survey.id }
        deleted.append(Tombstone(id: survey.id, type: "survey", deleted: Stamp.now(), date: survey.date))
        changed()
    }

    // MARK: - Sync

    func state() -> SyncState {
        return SyncState(entries: entries, events: events, deleted: deleted, dreams: dreams, surveys: surveys, changed: nil)
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

        var dreamsByID: [String: Dream] = [:]
        for d in dreams {
            if let old = dreamsByID[d.id], old.updated >= d.updated { continue }
            dreamsByID[d.id] = d
        }
        for d in other.dreams ?? [] {  // an older PC sends none: keep ours as they are
            if let t = tombs[d.id], t.deleted >= d.updated { continue }
            if let mine = dreamsByID[d.id] {
                if d.updated > mine.updated {
                    dreamsByID[d.id] = d
                    changes += 1
                }
            } else if dreamsByID.values.contains(where: {
                $0.date == d.date && $0.feeling == d.feeling && $0.tags == d.tags && $0.text == d.text
            }) {
                continue
            } else {
                dreamsByID[d.id] = d
                changes += 1
            }
        }
        for t in tombs.values where t.type == "dream" {
            if let mine = dreamsByID[t.id], mine.updated <= t.deleted {
                dreamsByID.removeValue(forKey: t.id)
                changes += 1
            }
        }

        var surveysByID: [String: Survey] = [:]
        for s in surveys {
            if let old = surveysByID[s.id], old.updated >= s.updated { continue }
            surveysByID[s.id] = s
        }
        for s in other.surveys ?? [] where s.isValid {  // an older PC sends none: keep ours
            if let t = tombs[s.id], t.deleted >= s.updated { continue }
            if let mine = surveysByID[s.id] {
                if s.updated > mine.updated {
                    surveysByID[s.id] = s
                    changes += 1
                }
            } else {
                surveysByID[s.id] = s
                changes += 1
            }
        }
        for t in tombs.values where t.type == "survey" {
            if let mine = surveysByID[t.id], mine.updated <= t.deleted {
                surveysByID.removeValue(forKey: t.id)
                changes += 1
            }
        }

        entries = Array(byID.values)
        surveys = Array(surveysByID.values).sorted { ($0.date, $0.time) < ($1.date, $1.time) }
        events = Array(eventsByID.values).sorted { $0.date < $1.date }
        dreams = Array(dreamsByID.values).sorted { $0.date < $1.date }
        deleted = Array(tombs.values)
        save()
        return changes
    }
}
