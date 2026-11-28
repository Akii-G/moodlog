import Foundation
import UIKit

/// Dose on this iPhone, without a PC. iPhone apps can't read each other's files, and on some
/// phones and install methods the Files picker can't open a file at all; app links work
/// everywhere. So Sottovoce asks Dose with dose://share, and Dose answers by opening
///   sottovoce://dose#<base64url of the zlib-compressed JSON, dose.json's format>
/// Sottovoce keeps the last copy it got (dose-from-dose.json) and reads it like the PC reads
/// dose.json. feed() is the PC's DoseMedication and Android's DoseLink.feed, the same rules.
enum DoseLink {
    private static let days = 400      // how far back unmarked doses are filled in

    private static var file: URL? {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        guard let dir else { return nil }
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("dose-from-dose.json")
    }

    /// Dose 1.4.1 or newer is on this iPhone (it answers dose://).
    @MainActor
    static var doseInstalled: Bool {
        URL(string: "dose://").map { UIApplication.shared.canOpenURL($0) } ?? false
    }

    static var hasCopy: Bool { file.map { FileManager.default.fileExists(atPath: $0.path) } ?? false }

    static var lastUpdate: Date? {
        guard let file else { return nil }
        return (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
    }

    /// Ask Dose for its medicines: iOS switches to Dose, which sends them straight back.
    @MainActor
    static func askDose() {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "doseAsked")
        if let url = URL(string: "dose://share") { UIApplication.shared.open(url) }
    }

    /// With "Update when I open Sottovoce" on: ask Dose, at most every 15 minutes (never in a
    /// loop, even if Dose doesn't answer).
    @MainActor
    static func askIfDue() {
        guard UserDefaults.standard.bool(forKey: "doseAuto"), doseInstalled else { return }
        let asked = UserDefaults.standard.double(forKey: "doseAsked")
        let fresh = lastUpdate.map { Date().timeIntervalSince($0) < 15 * 60 } ?? false
        if fresh || Date().timeIntervalSince1970 - asked < 15 * 60 { return }
        askDose()
    }

    /// Take Dose's data from sottovoce://dose#…; false if the link isn't one.
    static func receive(_ url: URL) -> Bool {
        guard url.scheme == "sottovoce", url.host == "dose", let fragment = url.fragment, !fragment.isEmpty else { return false }
        var b64 = fragment.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while b64.count % 4 != 0 { b64 += "=" }
        guard let packed = Data(base64Encoded: b64),
              let json = try? (packed as NSData).decompressed(using: .zlib) as Data,
              let doc = (try? JSONSerialization.jsonObject(with: json)) as? [String: Any], doc["medicines"] is [Any],
              let file
        else { return false }
        return (try? json.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])) != nil
    }

    /// Forget Dose's data on this iPhone.
    static func forget() {
        if let file { try? FileManager.default.removeItem(at: file) }
    }

    /// Dose's data as last received, as the feed; nil when there is none.
    static func read() -> DoseFeed? {
        guard let file, let data = try? Data(contentsOf: file),
              let doc = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }
        return feed(doc)
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
