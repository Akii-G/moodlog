import Foundation

/// Optional words next to a mood, more precise than five faces. Nouns, so they read the same for
/// everyone in Italian too. The keys are stored and synced; the same list in every app.
enum Feeling {
    static let all: [(key: String, label: String)] = [
        ("joy", tr("Joy")), ("calm", tr("Calm")), ("gratitude", tr("Gratitude")), ("pride", tr("Pride")), ("hope", tr("Hope")),
        ("relief", tr("Relief")), ("motivation", tr("Motivation")), ("connection", tr("Connection")),
        ("sadness", tr("Sadness")), ("worry", tr("Worry")), ("tiredness", tr("Tiredness")), ("loneliness", tr("Loneliness")),
        ("irritation", tr("Irritation")), ("overwhelm", tr("Overwhelm")), ("emptiness", tr("Emptiness")), ("guilt", tr("Guilt")),
        ("boredom", tr("Boredom")), ("stress", tr("Stress")),
    ]

    static func label(_ key: String) -> String {
        all.first { $0.key == key }?.label ?? key
    }

    /// Known keys only, in the list's order.
    static func clean(_ keys: [String]) -> [String] {
        let wanted = Set(keys.map { $0.lowercased() })
        return all.map(\.key).filter { wanted.contains($0) }
    }

    static func text(_ keys: [String]) -> String {
        clean(keys).map(label).joined(separator: ", ")
    }

    /// [(key, how many entries)] in a period, most often first; private entries left out.
    @MainActor
    static func counts(_ store: AppStore, from start: String, to end: String) -> [(String, Int)] {
        var counts: [String: Int] = [:]
        for e in store.entries where e.date >= start && e.date <= end && !e.hidden {
            for k in clean(e.feelings ?? []) { counts[k, default: 0] += 1 }
        }
        let order = all.map(\.key)
        return counts.sorted { a, b in
            a.value != b.value ? a.value > b.value : (order.firstIndex(of: a.key) ?? 0) < (order.firstIndex(of: b.key) ?? 0)
        }.map { ($0.key, $0.value) }
    }
}

/// Optional tags for what you did, to see later what helps. The keys are stored and synced; the
/// same list, in the same order, in every app.
enum Activity {
    static let all: [(key: String, label: String)] = [
        ("work", tr("Work")), ("study", tr("Study")), ("family", tr("Family")), ("friends", tr("Friends")),
        ("partner", tr("Partner")), ("exercise", tr("Exercise")), ("walk", tr("Walk")), ("outdoors", tr("Outdoors")),
        ("hobby", tr("Hobby")), ("gaming", tr("Gaming")), ("reading", tr("Reading")), ("music", tr("Music")),
        ("shows", tr("TV and series")), ("cooking", tr("Cooking")), ("chores", tr("Chores")), ("rest", tr("Rest")),
    ]

    static func label(_ key: String) -> String {
        all.first { $0.key == key }?.label ?? key
    }

    /// Known keys only, in the list's order.
    static func clean(_ keys: [String]) -> [String] {
        let wanted = Set(keys.map { $0.lowercased() })
        return all.map(\.key).filter { wanted.contains($0) }
    }

    static func text(_ keys: [String]) -> String {
        clean(keys).map(label).joined(separator: ", ")
    }

    /// [(key, how many entries)] in a period, most often first; private entries left out.
    @MainActor
    static func counts(_ store: AppStore, from start: String, to end: String) -> [(String, Int)] {
        var counts: [String: Int] = [:]
        for e in store.entries where e.date >= start && e.date <= end && !e.hidden {
            for k in clean(e.activities ?? []) { counts[k, default: 0] += 1 }
        }
        let order = all.map(\.key)
        return counts.sorted { a, b in
            a.value != b.value ? a.value > b.value : (order.firstIndex(of: a.key) ?? 0) < (order.firstIndex(of: b.key) ?? 0)
        }.map { ($0.key, $0.value) }
    }
}

/// Writing prompts: a gentle question when the note is empty. One a day, or another one when
/// asked. The same list, in the same order, in every app.
enum Prompts {
    static let all = [
        tr("What took most of your energy today?"),
        tr("What's one small thing that went well?"),
        tr("Is there something you're carrying that you'd like to put down here?"),
        tr("Who did you talk to today, and how did it feel?"),
        tr("What would you like to tell yourself tonight?"),
        tr("How did you sleep, and how did it shape the day?"),
        tr("What made you smile, even a little?"),
        tr("What's on your mind right now?"),
        tr("What did your body need today?"),
        tr("Was there a moment you felt like yourself?"),
        tr("What's worrying you, in a few words?"),
        tr("What are you looking forward to, even something small?"),
        tr("What would make tomorrow a bit easier?"),
        tr("What did you do just for you today?"),
    ]

    /// The day's question (the same as on the PC: days since 1 January of year 1, like Python's
    /// toordinal); offset moves to the next ones.
    static func forDay(_ day: String, offset: Int = 0) -> String {
        guard let d = Day.date(day) else { return all[0] }
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let parts = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: d)
        let noon = utc.date(from: DateComponents(year: parts.year, month: parts.month, day: parts.day, hour: 12)) ?? d
        // 1970-01-01 is day 719163 in Python's ordinal count
        let ordinal = Int((noon.timeIntervalSince1970 / 86_400).rounded(.down)) + 719_163
        let i = ((ordinal + offset) % all.count + all.count) % all.count
        return all[i]
    }
}
