import Foundation

/// Backup to a file you save once where you like: in iCloud Drive or any place in Files. Moodlog
/// then keeps that file up to date after each change; iCloud Drive uploads it, Moodlog itself
/// never goes on the internet. The file is the same as Moodlog for Android's backup, so a
/// backup restores on either phone. Restoring merges: nothing on the phone is lost.
///
/// (Choosing a folder instead doesn't work with every iPhone and install method: the picker's
/// Open button can do nothing. Saving one file works everywhere.)
enum Backup {
    static let fileName = "sottovoce-backup.json"
    private static let key = "backupFile"

    struct File: Codable {
        var entries: [Entry]
        var events: [MoodEvent]
        var deleted: [Tombstone]
        var dreams: [Dream]?
        var surveys: [Survey]?
        var moodlog_backup: Int?
        var exported: String?
    }

    @MainActor
    static func content(_ store: AppStore) throws -> Data {
        let file = File(entries: store.entries, events: store.events, deleted: store.deleted,
                        dreams: store.dreams, surveys: store.surveys, moodlog_backup: 1, exported: Stamp.now())
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(file)
    }

    static func parse(_ data: Data) throws -> SyncState {
        let file = try JSONDecoder().decode(File.self, from: data)
        return SyncState(entries: file.entries, events: file.events, deleted: file.deleted, dreams: file.dreams,
                         surveys: file.surveys)
    }

    /// A first copy in the app's temporary folder, for the Save picker to put where you choose.
    @MainActor
    static func draft(_ store: AppStore) throws -> URL {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("backup-draft", isDirectory: true)
        try? FileManager.default.removeItem(at: dir)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent(fileName)
        try content(store).write(to: url, options: .atomic)
        return url
    }

    /// The file picked in this session: used directly, it doesn't depend on the bookmark.
    nonisolated(unsafe) private static var picked: URL?

    /// Remembers the saved file (a security-scoped bookmark), so Moodlog can keep writing to it.
    static func remember(file: URL) throws {
        let ok = file.startAccessingSecurityScopedResource()
        defer { if ok { file.stopAccessingSecurityScopedResource() } }
        // No .minimalBookmark: on iPhone that kind of bookmark loses the permission to write.
        let data = try file.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil)
        picked = file
        UserDefaults.standard.set(data, forKey: key)
        UserDefaults.standard.set(file.deletingLastPathComponent().lastPathComponent + " › " + file.lastPathComponent,
                                  forKey: "backupFileName")
        UserDefaults.standard.set(Day.today + " " + Day.timeString(Date()), forKey: "lastBackup")
    }

    static func forget() {
        picked = nil
        UserDefaults.standard.removeObject(forKey: key)
        UserDefaults.standard.removeObject(forKey: "backupFileName")
    }

    /// Where the file is, e.g. "Sottovoce backup › sottovoce-backup.json"; nil when none was chosen.
    static var placeName: String? { UserDefaults.standard.string(forKey: "backupFileName") }

    /// Why the saved file can't be reached, when it can't (shown in Settings).
    nonisolated(unsafe) static var problem: String?

    private static func file() -> URL? {
        if let picked { return picked }
        guard let data = UserDefaults.standard.data(forKey: key) else {
            problem = tr("No backup file chosen.")
            return nil
        }
        var stale = false
        do {
            let url = try URL(resolvingBookmarkData: data, options: [], relativeTo: nil, bookmarkDataIsStale: &stale)
            if stale { try? remember(file: url) }
            picked = url
            problem = nil
            return url
        } catch {
            problem = error.localizedDescription
            return nil
        }
    }

    /// True when a file was chosen but Moodlog can no longer reach it (for example after
    /// installing a new version with a sideloading app, which can change how iOS sees the app).
    static var lostFile: Bool { placeName != nil && file() == nil }

    /// Writes the backup over the saved file; true if it worked.
    @MainActor
    @discardableResult
    static func write(_ store: AppStore) -> Bool { writeError(store) == nil }

    /// Writes the backup over the saved file; nil if it worked, otherwise why not.
    /// The file is overwritten in place, not replaced: Moodlog may change that one file, but
    /// not create new ones next to it, which is what an "atomic" save would need.
    @MainActor
    static func writeError(_ store: AppStore) -> String? {
        guard let url = file() else {
            return tr("Sottovoce can't reach the backup file any more (%@). Tap Choose another file… and save it again.",
                      problem ?? tr("No backup file chosen."))
        }
        let data: Data
        do { data = try content(store) } catch { return error.localizedDescription }
        let ok = url.startAccessingSecurityScopedResource()
        defer { if ok { url.stopAccessingSecurityScopedResource() } }
        var coordination: NSError?
        var failure: String?
        NSFileCoordinator().coordinate(writingItemAt: url, options: [], error: &coordination) { target in
            do {
                let handle = try FileHandle(forWritingTo: target)
                defer { try? handle.close() }
                try handle.truncate(atOffset: 0)
                try handle.write(contentsOf: data)
            } catch {
                // Last try: a plain (not atomic) write.
                do { try data.write(to: target, options: []) } catch let second {
                    failure = second.localizedDescription
                }
            }
        }
        if let coordination { return coordination.localizedDescription }
        if let failure { return failure }
        UserDefaults.standard.set(Day.today + " " + Day.timeString(Date()), forKey: "lastBackup")
        return nil
    }

    static func read(_ url: URL) throws -> SyncState {
        let ok = url.startAccessingSecurityScopedResource()
        defer { if ok { url.stopAccessingSecurityScopedResource() } }
        return try parse(Data(contentsOf: url))
    }
}
