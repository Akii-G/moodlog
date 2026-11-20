import Foundation

/// Backup to a file you save once where you like: in iCloud Drive or any place in Files. Moodlog
/// then keeps that file up to date after each change; iCloud Drive uploads it, Moodlog itself
/// never goes on the internet. The file is the same as Moodlog for Android's backup, so a
/// backup restores on either phone. Restoring merges: nothing on the phone is lost.
///
/// (Choosing a folder instead doesn't work with every iPhone and install method: the picker's
/// Open button can do nothing. Saving one file works everywhere.)
enum Backup {
    static let fileName = "moodlog-backup.json"
    private static let key = "backupFile"

    struct File: Codable {
        var entries: [Entry]
        var events: [MoodEvent]
        var deleted: [Tombstone]
        var dreams: [Dream]?
        var moodlog_backup: Int?
        var exported: String?
    }

    @MainActor
    static func content(_ store: AppStore) throws -> Data {
        let file = File(entries: store.entries, events: store.events, deleted: store.deleted,
                        dreams: store.dreams, moodlog_backup: 1, exported: Stamp.now())
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(file)
    }

    static func parse(_ data: Data) throws -> SyncState {
        let file = try JSONDecoder().decode(File.self, from: data)
        return SyncState(entries: file.entries, events: file.events, deleted: file.deleted, dreams: file.dreams)
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

    /// Remembers the saved file (a security-scoped bookmark), so Moodlog can keep writing to it.
    static func remember(file: URL) throws {
        let ok = file.startAccessingSecurityScopedResource()
        defer { if ok { file.stopAccessingSecurityScopedResource() } }
        let data = try file.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        UserDefaults.standard.set(data, forKey: key)
        UserDefaults.standard.set(file.deletingLastPathComponent().lastPathComponent + " › " + file.lastPathComponent,
                                  forKey: "backupFileName")
        UserDefaults.standard.set(Day.today + " " + Day.timeString(Date()), forKey: "lastBackup")
    }

    static func forget() {
        UserDefaults.standard.removeObject(forKey: key)
        UserDefaults.standard.removeObject(forKey: "backupFileName")
    }

    /// Where the file is, e.g. "Moodlog backup › moodlog-backup.json"; nil when none was chosen.
    static var placeName: String? { UserDefaults.standard.string(forKey: "backupFileName") }

    private static func file() -> URL? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        var stale = false
        guard let url = try? URL(resolvingBookmarkData: data, options: [], relativeTo: nil, bookmarkDataIsStale: &stale) else { return nil }
        if stale { try? remember(file: url) }
        return url
    }

    /// Writes the backup over the saved file; true if it worked.
    @MainActor
    @discardableResult
    static func write(_ store: AppStore) -> Bool {
        guard let url = file(), let data = try? content(store) else { return false }
        let ok = url.startAccessingSecurityScopedResource()
        defer { if ok { url.stopAccessingSecurityScopedResource() } }
        var error: NSError?
        var written = false
        NSFileCoordinator().coordinate(writingItemAt: url, options: .forReplacing, error: &error) { target in
            written = (try? data.write(to: target, options: .atomic)) != nil
        }
        if written && error == nil {
            UserDefaults.standard.set(Day.today + " " + Day.timeString(Date()), forKey: "lastBackup")
            return true
        }
        return false
    }

    static func read(_ url: URL) throws -> SyncState {
        let ok = url.startAccessingSecurityScopedResource()
        defer { if ok { url.stopAccessingSecurityScopedResource() } }
        return try parse(Data(contentsOf: url))
    }
}
