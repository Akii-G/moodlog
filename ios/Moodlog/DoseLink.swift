import Foundation

/// Dose on this iPhone, without a PC: Dose keeps a file up to date where the user saved it once
/// (Dose › Settings › Share with Sottovoce, for example in On My iPhone or iCloud Drive), and
/// Sottovoce reads that file, chosen once here. Both remember it as a security-scoped bookmark.
/// (A file, not a folder: picking a folder doesn't work with every iPhone and install method.)
/// feed() is the PC's DoseMedication and Android's DoseLink.feed, the same rules.
enum DoseLink {
    private static let key = "doseFile"
    private static let days = 400      // how far back unmarked doses are filled in
    /// The file picked in this session: used directly, it doesn't depend on the bookmark.
    nonisolated(unsafe) private static var picked: URL?

    static var isLinked: Bool { UserDefaults.standard.data(forKey: key) != nil }

    /// Where the file is, e.g. "Dose › dose-for-sottovoce.json".
    static var placeName: String? { UserDefaults.standard.string(forKey: "doseFileName") }

    /// Remember the file the user picked.
    static func link(_ file: URL) throws {
        let scoped = file.startAccessingSecurityScopedResource()
        defer { if scoped { file.stopAccessingSecurityScopedResource() } }
        let bookmark = try file.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil)
        picked = file
        UserDefaults.standard.set(bookmark, forKey: key)
        UserDefaults.standard.set(file.deletingLastPathComponent().lastPathComponent + " › " + file.lastPathComponent,
                                  forKey: "doseFileName")
    }

    static func unlink() {
        picked = nil
        UserDefaults.standard.removeObject(forKey: key)
        UserDefaults.standard.removeObject(forKey: "doseFileName")
    }

    private static func file() -> URL? {
        if let picked { return picked }
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        var stale = false
        guard let url = try? URL(resolvingBookmarkData: data, options: [], relativeTo: nil, bookmarkDataIsStale: &stale)
        else { return nil }
        if stale { try? link(url) }
        picked = url
        return url
    }

    enum Result {
        case notLinked
        case unreadable
        case notDose
        case feed(DoseFeed, Date?)
    }

    /// Read Dose's file.
    static func read() -> Result {
        guard isLinked else { return .notLinked }
        guard let file = file() else { return .unreadable }
        let scoped = file.startAccessingSecurityScopedResource()
        defer { if scoped { file.stopAccessingSecurityScopedResource() } }
        var data: Data?
        var error: NSError?
        NSFileCoordinator().coordinate(readingItemAt: file, options: [], error: &error) { url in
            data = try? Data(contentsOf: url)
        }
        guard let data else { return .unreadable }
        guard let doc = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              doc["medicines"] is [Any] else { return .notDose }
        let modified = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
        return .feed(feed(doc), modified)
    }

    // MARK: - Dose's file as the feed the day view and the report use

    private static var units: [String: (String, String)] {
        ["pill": (tr("pill"), tr("pills")), "capsule": (tr("capsule"), tr("capsules")),
         "drop": (tr("drop"), tr("drops")), "ml": ("ml", "ml"), "puff": (tr("puff"), tr("puffs")),
         "sachet": (tr("sachet"), tr("sachets")), "patch": (tr("patch"), tr("patches")),
         "injection": (tr("injection"), tr("injections")), "application": (tr("application"), tr("applications"))]
    }

    /// 1.5, "pill" -> "1½ pills".
    static func amountText(_ amount: Double, _ unit: String) -> String {
        let (one, many) = units[unit] ?? (unit, unit)
        let whole = Int(amount)
        let frac = ((amount - Double(whole)) * 100).rounded() / 100
        let glyph = [0.25: "¼", 0.5: "½", 0.75: "¾"][frac]
        let num: String
        if frac == 0 {
            num = String(whole)
        } else if let glyph, unit != "ml" {
            num = (whole > 0 ? String(whole) : "") + glyph
        } else {
            let italian = (Bundle.main.preferredLocalizations.first ?? "en").hasPrefix("it")
            num = String(format: "%g", amount).replacingOccurrences(of: ".", with: italian ? "," : ".")
        }
        return num + " " + (amount <= 1 ? one : many)
    }

    private static func dueOn(_ s: [String: Any], _ day: String) -> Bool {
        let kind = s["kind"] as? String ?? "daily"
        let start = (s["start"] as? String).flatMap { Day.date($0) != nil ? $0 : nil }
        let end = (s["end"] as? String).flatMap { Day.date($0) != nil ? $0 : nil }
        if kind == "needed" { return false }
        if let start, day < start { return false }
        if let end, day > end { return false }
        switch kind {
        case "weekdays":
            guard let date = Day.date(day) else { return false }
            let monday1 = (Calendar(identifier: .gregorian).component(.weekday, from: date) + 5) % 7 + 1
            return ((s["weekdays"] as? [Any]) ?? []).contains { ($0 as? NSNumber)?.intValue == monday1 }
        case "every":
            let every = max((s["every"] as? NSNumber)?.intValue ?? 1, 1)
            guard let a = Day.date(start ?? day), let b = Day.date(day) else { return false }
            let gap = Calendar(identifier: .gregorian).dateComponents([.day], from: a, to: b).day ?? 0
            return gap % every == 0
        default:
            return true
        }
    }

    static func feed(_ doc: [String: Any], today: String = Day.today) -> DoseFeed {
        var meds: [String: [String: Any]] = [:]
        for m in doc["medicines"] as? [[String: Any]] ?? [] {
            if let id = m["id"] as? String, !id.isEmpty, let name = m["name"] as? String, !name.isEmpty { meds[id] = m }
        }
        func title(_ m: [String: Any]) -> String {
            let strength = m["strength"] as? String ?? ""
            return (m["name"] as? String ?? "") + (strength.isEmpty ? "" : " " + strength)
        }
        func number(_ v: Any?, _ fallback: Double) -> Double { (v as? NSNumber)?.doubleValue ?? fallback }
        var items: [DoseFeed.Item] = []
        var marked = Set<String>()
        for d in doc["doses"] as? [[String: Any]] ?? [] {
            guard let medID = d["med"] as? String, let m = meds[medID] else { continue }
            let status = d["status"] as? String ?? ""
            let day = d["day"] as? String ?? ""
            guard status == "taken" || status == "skipped", day.count == 10 else { continue }
            let time = d["time"] as? String ?? "", at = d["at"] as? String ?? ""
            marked.insert(medID + "|" + day + "|" + time)
            let when = status == "taken" && !at.isEmpty ? at : (time.isEmpty ? at : time)
            items.append(DoseFeed.Item(date: day, time: when, status: status,
                                       amount: amountText(number(d["amount"], 1), m["unit"] as? String ?? "pill"),
                                       name: title(m)))
        }
        // scheduled doses nobody marked, up to today: not taken, or yet to take
        for (id, m) in meds {
            guard let s = m["schedule"] as? [String: Any], (m["archived"] as? Bool) != true,
                  (s["kind"] as? String) != "needed" else { continue }
            let start = (s["start"] as? String).flatMap { Day.date($0) != nil ? $0 : nil } ?? today
            var day = max(Day.adding(-days, to: today), start)
            let times = (s["times"] as? [Any] ?? []).compactMap { $0 as? String }
            let own = s["amounts"] as? [String: Any] ?? [:]
            while day <= today {
                if dueOn(s, day) {
                    for t in times where !marked.contains(id + "|" + day + "|" + t) {
                        let amount = number(own[t], number(m["amount"], 1))
                        items.append(DoseFeed.Item(date: day, time: t, status: "pending",
                                                   amount: amountText(amount, m["unit"] as? String ?? "pill"),
                                                   name: title(m)))
                    }
                }
                day = Day.adding(1, to: day)
            }
        }
        items.sort { ($0.date, $0.time, $0.name) < ($1.date, $1.time, $1.name) }
        let list = meds.values.map { m in
            DoseFeed.Medicine(name: title(m), archived: (m["archived"] as? Bool) ?? false,
                              asNeeded: ((m["schedule"] as? [String: Any])?["kind"] as? String) == "needed",
                              ingredient: m["ingredient"] as? String ?? "")
        }.sorted { $0.name < $1.name }
        return DoseFeed(medicines: list, doses: items)
    }
}
