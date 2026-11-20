import Foundation

/// "What's new": this app's changelog (changelog.json, made by tools/phone_changelog.py from
/// src/moodlog), shown once after an update and from Settings.
enum WhatsNew {
    struct Version: Decodable, Identifiable {
        let version: String
        let date: String
        let items: [String: [String]]
        var id: String { version }

        var lines: [String] {
            let lang = (Locale.preferredLanguages.first ?? "en").hasPrefix("it") ? "it" : "en"
            return items[lang] ?? items["en"] ?? []
        }
    }

    static func load() -> [Version] {
        guard let url = Bundle.main.url(forResource: "changelog", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([Version].self, from: data)) ?? []
    }

    static var current: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    /// "1.10.0" > "1.9.2".
    static func newer(_ a: String, than b: String) -> Bool {
        let x = a.split(separator: ".").map { Int($0) ?? 0 }, y = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0
            if p != q { return p > q }
        }
        return false
    }

    /// The version to mark new things from, or nil when nothing should be shown: a brand-new
    /// install (it gets the tour) or no change. Coming from a version that didn't record this
    /// (1.4 and older), only this version is marked new.
    static func since(lastSeen: String, usedBefore: Bool) -> String? {
        if lastSeen.isEmpty {
            guard usedBefore else { return nil }
            let versions = load()
            return versions.count > 1 ? versions[1].version : "0"
        }
        return newer(current, than: lastSeen) ? lastSeen : nil
    }
}
