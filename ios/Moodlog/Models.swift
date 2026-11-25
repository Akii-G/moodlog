import Foundation

// The same records Moodlog on the PC keeps. Field names match the PC's /api/sync JSON.

struct Entry: Codable, Identifiable, Hashable {
    var id: String
    var date: String      // "YYYY-MM-DD"
    var time: String      // "HH:MM" or "" when no time was given
    var mood: Int         // 1-5, 0 = not given
    var energy: Int       // 1-5, 0 = not given
    var anxiety: Int      // 1-5, 0 = not given
    var note: String
    var updated: String   // UTC change time, e.g. 2026-11-19T05:20:31.123Z
    /// Ticked private: never in the doctor report. Sent as "private": true only when set.
    var isPrivate: Bool? = nil
    /// Feeling words (keys like "joy", "tiredness"); nil or empty when none were picked.
    var feelings: [String]? = nil
    /// Activity tags (keys like "work", "walk"); nil or empty when none were picked.
    var activities: [String]? = nil

    var hidden: Bool { isPrivate == true }

    enum CodingKeys: String, CodingKey {
        case id, date, time, mood, energy, anxiety, note, updated, feelings, activities
        case isPrivate = "private"
    }
}

struct MoodEvent: Codable, Identifiable, Hashable {
    var id: String
    var date: String
    var kind: String      // medication, health, life, other
    var title: String
    var note: String
    var updated: String
}

/// A dream journal entry. `date` is the morning you woke up.
struct Dream: Codable, Identifiable, Hashable {
    var id: String
    var date: String
    var feeling: String   // "", pleasant, neutral, disturbing
    var tags: [String]    // nightmare, vivid, recurring, lucid, forgotten
    var text: String
    var updated: String
    /// Ticked private: counted in the doctor report, but its text never shown.
    var isPrivate: Bool? = nil

    var hidden: Bool { isPrivate == true }

    enum CodingKeys: String, CodingKey {
        case id, date, feeling, tags, text, updated
        case isPrivate = "private"
    }

    var isNightmare: Bool { return tags.contains("nightmare") || feeling == "disturbing" }

    /// Feeling and tags as one line: "Disturbing · Nightmare, Vivid".
    var details: String {
        var parts: [String] = []
        if let f = DreamFeeling(rawValue: feeling) { parts.append(f.label) }
        let names = DreamTag.allCases.filter { tags.contains($0.rawValue) }.map { $0.label }
        if !names.isEmpty { parts.append(names.joined(separator: ", ")) }
        return parts.joined(separator: " · ")
    }

    /// A short line for lists: the start of the text, or the details if there is none.
    var title: String {
        let words = text.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        return words.isEmpty ? details : words
    }
}

enum DreamFeeling: String, CaseIterable, Identifiable {
    case pleasant, neutral, disturbing
    var id: String { return rawValue }
    var label: String {
        switch self {
        case .pleasant: return tr("Pleasant")
        case .neutral: return tr("Neutral")
        case .disturbing: return tr("Disturbing")
        }
    }
}

enum DreamTag: String, CaseIterable, Identifiable {
    case nightmare, vivid, recurring, lucid, forgotten
    var id: String { return rawValue }
    var label: String {
        switch self {
        case .nightmare: return tr("Nightmare")
        case .vivid: return tr("Vivid")
        case .recurring: return tr("Recurring")
        case .lucid: return tr("Lucid")
        case .forgotten: return tr("Didn't remember")
        }
    }
}

struct Tombstone: Codable, Hashable {
    var id: String
    var type: String      // "entry", "event", "dream", "survey" or "point"
    var deleted: String   // UTC time of the deletion
    var date: String?
}

struct SyncState: Codable {
    var entries: [Entry]
    var events: [MoodEvent]
    var deleted: [Tombstone]
    var dreams: [Dream]?  // missing when the PC runs Moodlog older than 2.3
    var surveys: [Survey]? = nil  // missing when the PC runs Moodlog older than 2.10
    var changed: Int?
    var appointment: Appointment? = nil  // missing when the PC runs Sottovoce older than 3.0.2
    var points: [TalkPoint]? = nil
    var dose: DoseFeed? = nil   // Dose's medication, from a PC with Dose installed
}

enum Mood {
    static let names = [1: "Awful", 2: "Bad", 3: "Okay", 4: "Good", 5: "Great"]

    static func name(_ value: Int) -> String {
        return names[value].map { tr($0) } ?? ""
    }

    /// Average mood -> nearest whole level, halves round up (same as the PC).
    static func level(_ average: Double) -> Int {
        return max(1, min(5, Int(average + 0.5)))
    }
}

enum Scale {
    static var energyWords: [Int: String] {
        return [1: tr("very low"), 2: tr("low"), 3: tr("medium"), 4: tr("high"), 5: tr("very high")]
    }
    static var anxietyWords: [Int: String] {
        return [1: tr("calm"), 2: tr("a little"), 3: tr("some"), 4: tr("a lot"), 5: tr("very anxious")]
    }
}

enum EventKind: String, CaseIterable, Identifiable {
    case medication, health, life, other

    var id: String { return rawValue }

    var label: String {
        switch self {
        case .medication: return tr("Medication change")
        case .health: return tr("Health")
        case .life: return tr("Life event")
        case .other: return tr("Other")
        }
    }

    static func label(for raw: String) -> String {
        return (EventKind(rawValue: raw) ?? .other).label
    }
}

enum Stamp {
    static let formatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        f.timeZone = TimeZone(identifier: "UTC")
        return f
    }()

    static func now() -> String {
        return formatter.string(from: Date())
    }
}

enum Day {
    static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.firstWeekday = 2  // Monday
        return c
    }()

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        f.dateFormat = "HH:mm"
        return f
    }()

    static func string(_ date: Date) -> String {
        return dayFormatter.string(from: date)
    }

    static func date(_ day: String) -> Date? {
        return dayFormatter.date(from: day)
    }

    static var today: String {
        return string(Date())
    }

    static func timeString(_ date: Date) -> String {
        return timeFormatter.string(from: date)
    }

    /// "HH:MM" on a given day -> Date (for the time picker).
    static func time(_ hhmm: String, on day: String) -> Date? {
        guard let base = date(day) else { return nil }
        let parts = hhmm.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        return calendar.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: base)
    }

    static func adding(_ days: Int, to day: String) -> String {
        guard let d = date(day), let moved = calendar.date(byAdding: .day, value: days, to: d) else { return day }
        return string(moved)
    }

    static func pretty(_ day: String, style: String = "EEEE d MMMM") -> String {
        guard let d = date(day) else { return day }
        let f = DateFormatter()
        f.dateFormat = style
        let text = f.string(from: d)   // in the app's language: "lunedì 16 novembre" in Italian
        return text.prefix(1).uppercased() + text.dropFirst()
    }

    /// Mon … Sun in the app's language, Monday first.
    static var weekdayInitials: [String] {
        let f = DateFormatter()
        let names = f.shortStandaloneWeekdaySymbols ?? ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
        return (Array(names[1...]) + [names[0]]).map { $0.prefix(1).uppercased() + $0.dropFirst() }
    }

    static func monthStart(_ date: Date) -> Date {
        let parts = calendar.dateComponents([.year, .month], from: date)
        return calendar.date(from: parts) ?? date
    }

    static func addMonths(_ n: Int, to month: Date) -> Date {
        return calendar.date(byAdding: .month, value: n, to: month) ?? month
    }

    /// Every day of the month as "YYYY-MM-DD".
    static func days(inMonth month: Date) -> [String] {
        guard let range = calendar.range(of: .day, in: .month, for: month) else { return [] }
        return range.compactMap { n in
            calendar.date(byAdding: .day, value: n - 1, to: monthStart(month)).map(string)
        }
    }

    /// Cells for a Monday-first month grid; nil = empty cell before the 1st.
    static func grid(forMonth month: Date) -> [String?] {
        let first = monthStart(month)
        let weekday = calendar.component(.weekday, from: first)  // 1 = Sunday
        let lead = (weekday + 5) % 7
        return Array(repeating: nil, count: lead) + days(inMonth: month).map { Optional($0) }
    }

    static func monthTitle(_ month: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "LLLL yyyy"
        return f.string(from: month).capitalized
    }
}
