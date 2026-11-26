import Foundation

/// Fixes made once and applied by every Sottovoce, synced with the PC (corrections.json there,
/// class Corrections in src/moodlog, same rules):
///   nights  ["2026-11-11": stamp]  sleep deleted for good (the watch wasn't worn)
///   names   ["Aripiprazolo 10mg": {to: "Aripiprazolo Teva 15 mg", updated}]  a medicine's old
///           name and the one it's shown as; to "" undoes it. The newest change wins.
struct Corrections: Codable, Equatable {
    struct Rename: Codable, Equatable {
        var to: String
        var updated: String
    }

    var nights: [String: String] = [:]
    var names: [String: Rename] = [:]

    private static let key = "corrections"

    /// The corrections kept on this phone.
    static var current: Corrections {
        get {
            guard let data = UserDefaults.standard.data(forKey: key),
                  let saved = try? JSONDecoder().decode(Corrections.self, from: data) else { return Corrections() }
            return saved
        }
        set {
            if let data = try? JSONEncoder().encode(newValue) { UserDefaults.standard.set(data, forKey: key) }
        }
    }

    /// Take in the PC's corrections; returns how many changed here.
    @discardableResult
    mutating func absorb(_ other: Corrections) -> Int {
        var changes = 0
        for (day, stamp) in other.nights where nights[day] == nil && Day.date(day) != nil {
            nights[day] = stamp
            changes += 1
        }
        for (old, item) in other.names where !old.isEmpty && old != item.to {
            if let mine = names[old], mine.updated >= item.updated { continue }
            if names[old]?.to != item.to { changes += 1 }
            names[old] = item
        }
        return changes
    }

    var deletedNights: Set<String> { Set(nights.keys) }

    mutating func deleteNight(_ day: String) {
        if nights[day] == nil { nights[day] = Stamp.now() }
    }

    /// [old name: name shown], following a renamed name that was renamed again.
    var renames: [String: String] {
        var direct: [String: String] = [:]
        for (old, item) in names where !item.to.isEmpty { direct[old] = item.to }
        var out: [String: String] = [:]
        for old in direct.keys {
            var to = direct[old]!, seen: Set<String> = [old]
            while let next = direct[to], !seen.contains(to) {
                seen.insert(to)
                to = next
            }
            if to != old { out[old] = to }
        }
        return out
    }

    /// Show these names as `to` from now on (and the names already shown as one of them).
    mutating func rename(_ list: [String], to: String) {
        let stamp = Stamp.now()
        for (old, item) in names where list.contains(item.to) && old != to {
            names[old] = Rename(to: to, updated: stamp)
        }
        for name in list where name != to { names[name] = Rename(to: to, updated: stamp) }
        if names[to] != nil { names[to] = Rename(to: "", updated: stamp) }
    }

    mutating func undoRename(_ to: String) {
        let stamp = Stamp.now()
        for (old, item) in names where item.to == to { names[old] = Rename(to: "", updated: stamp) }
    }

    /// Doses with the new names. A medicine marked in Dose and logged in Health on the same day
    /// counts once: Dose's.
    static func apply(_ doses: [DoseRecord]) -> [DoseRecord] {
        let map = current.renames
        let renamed = doses.map { d -> DoseRecord in
            guard let to = map[d.name] else { return d }
            return DoseRecord(id: d.id, name: to, time: d.time, status: d.status, amount: d.amount,
                              unit: d.unit, label: d.label)
        }
        let inDose = Set(renamed.filter(\.fromDose).map { Day.string($0.time) + "|" + $0.name })
        return renamed.filter { $0.fromDose || !inDose.contains(Day.string($0.time) + "|" + $0.name) }
    }

    static func apply(_ meds: [MedicationInfo]) -> [MedicationInfo] {
        let map = current.renames
        var out: [String: MedicationInfo] = [:]
        for m in meds {
            let name = map[m.name] ?? m.name
            let archived = m.archived && (out[name]?.archived ?? true)
            out[name] = MedicationInfo(name: name, archived: archived, asNeeded: m.asNeeded)
        }
        return out.values.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    /// Groups of names that look like one medicine and aren't renamed yet: the same name before
    /// the strength, or the same active ingredient in Dose.
    static func suggestions(_ names: [String], ingredients: [String: String]) -> [[String]] {
        let makers: Set<String> = ["teva", "mylan", "sandoz", "eg", "doc", "ratiopharm", "zentiva", "aurobindo",
                                   "accord", "krka", "pensa", "hexal", "almus", "sun", "germed", "dorom", "abc",
                                   "aristo", "viatris"]
        func base(_ name: String) -> String {
            var words: [String] = []
            for w in name.lowercased().split(whereSeparator: \.isWhitespace).map(String.init) {
                if w.contains(where: \.isNumber) { break }
                if !makers.contains(w) && w != "·" && w != "-" { words.append(w) }
            }
            return words.joined(separator: " ")
        }
        var parent: [String: String] = [:]
        for n in names { parent[n] = n }
        func find(_ n: String) -> String {
            var n = n
            while let p = parent[n], p != n { n = p }
            return n
        }
        var seen: [String: String] = [:]
        for n in names {
            var keys: Set<String> = [base(n)]
            if let first = ingredients[n]?.lowercased().split(separator: " ").first { keys.insert(String(first)) }
            for key in keys where !key.isEmpty {
                if let other = seen[key] { parent[find(n)] = find(other) } else { seen[key] = n }
            }
        }
        var groups: [String: [String]] = [:]
        for n in names { groups[find(n), default: []].append(n) }
        let map = current.renames
        return groups.values
            .filter { g in g.count > 1 && Set(g.map { map[$0] ?? $0 }).count > 1 }
            .map { $0.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending } }
            .sorted { $0[0].localizedCaseInsensitiveCompare($1[0]) == .orderedAscending }
    }
}
