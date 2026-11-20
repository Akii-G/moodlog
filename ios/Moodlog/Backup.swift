import Foundation

/// Backup to a folder you pick once: in iCloud Drive (or another app in Files). Moodlog keeps
/// moodlog-backup.json there up to date after each change; iCloud Drive uploads it, Moodlog
/// itself never goes on the internet. The file is the same as Moodlog for Android's backup, so
/// a backup restores on either phone. Restoring merges: nothing on the phone is lost.
enum Backup {
    static let fileName = "moodlog-backup.json"
    private static let key = "backupFolder"

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

    /// Remembers the picked folder (a security-scoped bookmark), so Moodlog can write there later.
    static func remember(folder: URL) throws {
        let ok = folder.startAccessingSecurityScopedResource()
        defer { if ok { folder.stopAccessingSecurityScopedResource() } }
        let data = try folder.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        UserDefaults.standard.set(data, forKey: key)
        UserDefaults.standard.set(folder.lastPathComponent, forKey: "backupFolderName")
    }

    static func forget() {
        UserDefaults.standard.removeObject(forKey: key)
        UserDefaults.standard.removeObject(forKey: "backupFolderName")
    }

    static var folderName: String? { UserDefaults.standard.string(forKey: "backupFolderName") }

    private static func folder() -> URL? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        var stale = false
        guard let url = try? URL(resolvingBookmarkData: data, options: [], relativeTo: nil, bookmarkDataIsStale: &stale) else { return nil }
        if stale { try? remember(folder: url) }
        return url
    }

    /// Writes the backup; true if it worked.
    @MainActor
    @discardableResult
    static func write(_ store: AppStore) -> Bool {
        guard let dir = folder() else { return false }
        let ok = dir.startAccessingSecurityScopedResource()
        defer { if ok { dir.stopAccessingSecurityScopedResource() } }
        do {
            var error: NSError?
            var written = false
            NSFileCoordinator().coordinate(writingItemAt: dir.appendingPathComponent(fileName), options: .forReplacing, error: &error) { url in
                written = (try? content(store).write(to: url, options: .atomic)) != nil
            }
            if written {
                UserDefaults.standard.set(Day.today + " " + Day.timeString(Date()), forKey: "lastBackup")
            }
            return written && error == nil
        }
    }

    static func read(_ url: URL) throws -> SyncState {
        let ok = url.startAccessingSecurityScopedResource()
        defer { if ok { url.stopAccessingSecurityScopedResource() } }
        return try parse(Data(contentsOf: url))
    }
}
