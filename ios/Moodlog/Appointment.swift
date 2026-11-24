import Foundation

/// "Before the appointment": the next doctor or therapist appointment and the things to talk
/// about there. Synced with the PC like the rest: the appointment as a whole and each point on
/// its own, newest change wins; deleted points are kept as tombstones (type "point").
struct Appointment: Codable, Hashable {
    var date: String      // "YYYY-MM-DD", "" when there is none
    var time: String      // "HH:MM" or ""
    var with: String      // "Dr Rossi" or ""
    var previous: String  // the appointment before, where the recap starts; "" when unknown
    var updated: String
}

struct TalkPoint: Codable, Identifiable, Hashable {
    var id: String
    var text: String
    var done: Bool        // already said: kept out of the report
    var updated: String

    var isValid: Bool {
        !id.isEmpty && !updated.isEmpty && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

struct AppointmentRoute: Hashable {}

/// The calm summaries: the recap before an appointment and "Your week, gently". The same rules
/// and words as the PC.
enum Gently {
    static func daysBetween(_ a: String, _ b: String) -> Int {
        guard let x = Day.date(a), let y = Day.date(b) else { return 0 }
        return Day.calendar.dateComponents([.day], from: x, to: y).day ?? 0
    }

    /// The appointment card in the diary: from a week before until the day after.
    static func cardShown(_ appointment: Appointment?, today: String = Day.today) -> Bool {
        guard let a = appointment, !a.date.isEmpty else { return false }
        let days = daysBetween(today, a.date)
        return days >= -1 && days <= 7
    }

    /// Where "since the last appointment" starts: the previous one, or 30 days ago.
    static func recapStart(_ appointment: Appointment?, today: String = Day.today) -> String {
        if let prev = appointment?.previous, !prev.isEmpty, prev < today { return prev }
        return Day.adding(-29, to: today)
    }

    /// "Friday 27 November at 16:30, with Dr Rossi · in 3 days"
    static func when(_ a: Appointment, today: String = Day.today) -> String {
        var text = Day.pretty(a.date, style: "EEEE d MMMM")
        if !a.time.isEmpty { text = tr("%@ at %@", text, a.time) }
        if !a.with.isEmpty { text = tr("%@, with %@", text, a.with) }
        let days = daysBetween(today, a.date)
        let rel = days == 0 ? tr("today") : days == 1 ? tr("tomorrow") : days == -1 ? tr("yesterday")
            : days > 0 ? trn("in %@ day", "in %@ days", days) : trn("%@ day ago", "%@ days ago", -days)
        return text + " · " + rel
    }

    @MainActor
    static func recap(_ store: AppStore, from start: String, to end: String) -> String {
        let since = Day.pretty(start, style: "d MMMM")
        let inRange = store.entries.filter { $0.date >= start && $0.date <= end }
        let logged = Set(inRange.filter { $0.mood > 0 }.map(\.date))
        if logged.isEmpty { return tr("Since %@: no moods logged yet.", since) }
        let moods = inRange.map(\.mood).filter { $0 > 0 }
        var parts = [trn("%@ day logged", "%@ days logged", logged.count)]
        let common = (1...5).max { a, b in
            let (ca, cb) = (moods.filter { $0 == a }.count, moods.filter { $0 == b }.count)
            return ca != cb ? ca < cb : a < b
        } ?? 3
        parts.append(tr("mostly %@", Mood.name(common).lowercased()))
        let top = Feeling.counts(store, from: start, to: end).prefix(2).filter { $0.1 > 1 }.map { Feeling.label($0.0) }
        if !top.isEmpty { parts.append(tr("often %@", top.joined(separator: ", "))) }
        let events = store.events.filter { $0.date >= start && $0.date <= end }.count
        if events > 0 { parts.append(trn("%@ event", "%@ events", events)) }
        return tr("Since %@: %@.", since, parts.joined(separator: " · "))
    }

    /// From Sunday 18:00 until the end of Monday: a few plain sentences about the week (Monday
    /// to Sunday) that is ending or just ended. nil when there is nothing to say.
    @MainActor
    static func week(_ store: AppStore, now: Date = Date()) -> (monday: String, text: String)? {
        let weekday = Day.calendar.component(.weekday, from: now)   // 1 = Sunday, 2 = Monday
        let hour = Day.calendar.component(.hour, from: now)
        let today = Day.string(now)
        let monday: String
        if weekday == 1 && hour >= 18 {
            monday = Day.adding(-6, to: today)
        } else if weekday == 2 {
            monday = Day.adding(-7, to: today)
        } else {
            return nil
        }
        let sunday = Day.adding(6, to: monday)
        let days = (0..<7).map { Day.adding($0, to: monday) }
        let logged = days.filter { store.average(on: $0) != nil }
        if logged.isEmpty { return nil }
        let moods = store.entries.filter { $0.date >= monday && $0.date <= sunday }.map(\.mood).filter { $0 > 0 }
        let common = (1...5).max { a, b in
            let (ca, cb) = (moods.filter { $0 == a }.count, moods.filter { $0 == b }.count)
            return ca != cb ? ca < cb : a < b
        } ?? 3
        var lines = [trn("%@ day logged, mostly %@.", "%@ days logged, mostly %@.", logged.count,
                         Mood.name(common).lowercased())]
        if let top = Feeling.counts(store, from: monday, to: sunday).first, top.1 > 1 {
            lines.append(tr("%@ came up %@ times.", Feeling.label(top.0), "\(top.1)"))
        }
        if logged.count >= 2 {
            let avgs = logged.map { ($0, store.average(on: $0) ?? 0) }
            let best = avgs.max { a, b in a.1 != b.1 ? a.1 < b.1 : a.0 < b.0 }!
            if best.1 > (avgs.map(\.1).min() ?? 0) {
                lines.append(tr("%@ was your best day.", Day.pretty(best.0, style: "EEEE")))
            }
        }
        return (monday, lines.joined(separator: " "))
    }
}
