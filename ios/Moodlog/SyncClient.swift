import Foundation
import Observation
import UIKit

enum SyncError: LocalizedError {
    case notConfigured
    case badAddress
    case unauthorized
    case unreachable(String)
    case server(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return tr("Add your PC's address and token in Settings first.")
        case .badAddress:
            return tr("The address doesn't look right. It should be like http://pop-os.local:8765")
        case .unauthorized:
            return tr("Wrong token. Copy it again from Moodlog on your PC (Phone sync).")
        case .unreachable(let why):
            return tr("Can't reach your PC. Is it on, with Phone sync switched on, and is your iPhone on the same Wi-Fi? (%@)", why)
        case .server(let message):
            return message
        }
    }
}

enum SyncClient {
    static var token: String {
        return (UserDefaults.standard.string(forKey: "token") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static var isConfigured: Bool {
        let server = (UserDefaults.standard.string(forKey: "server") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return !server.isEmpty && !token.isEmpty
    }

    /// The address from Settings. Accepts "pop-os.local:8765", a full URL, or the
    /// shortcut's address ending in /health.
    static func baseURL() throws -> URL {
        var raw = (UserDefaults.standard.string(forKey: "server") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if raw.isEmpty || token.isEmpty { throw SyncError.notConfigured }
        if !raw.lowercased().hasPrefix("http://") && !raw.lowercased().hasPrefix("https://") {
            raw = "http://" + raw
        }
        while raw.hasSuffix("/") { raw.removeLast() }
        if raw.lowercased().hasSuffix("/health") { raw.removeLast("/health".count) }
        guard let url = URL(string: raw), url.host != nil else { throw SyncError.badAddress }
        return url
    }

    static func request(_ path: String, method: String = "GET", body: Data? = nil) async throws -> Data {
        let url = try baseURL().appendingPathComponent(path)
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.timeoutInterval = 20
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let body = body {
            req.httpBody = body
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let result: (Data, URLResponse)
        do {
            result = try await URLSession.shared.data(for: req)
        } catch {
            throw SyncError.unreachable(error.localizedDescription)
        }
        let data = result.0
        let response = result.1
        guard let http = response as? HTTPURLResponse else { throw SyncError.server(tr("No answer from your PC.")) }
        if http.statusCode == 401 { throw SyncError.unauthorized }
        if !(200..<300).contains(http.statusCode) {
            if let doc = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let message = doc["error"] as? String {
                throw SyncError.server(message)
            }
            let text = String(data: data, encoding: .utf8) ?? ""
            throw SyncError.server(text.isEmpty ? tr("Your PC answered with error %@.", String(http.statusCode)) : text)
        }
        return data
    }

    /// Checks the address and token. Returns e.g. "Connected to Moodlog 2.0.0".
    static func ping() async throws -> String {
        let data = try await request("api/ping")
        guard let doc = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let version = doc["version"] as? String else {
            throw SyncError.server(tr("That address answered, but it isn't Moodlog 2.0 or newer. Update Moodlog on your PC."))
        }
        return tr("Connected to Moodlog %@ on your PC", version)
    }

    private struct Outgoing: Codable {
        var entries: [Entry]
        var events: [MoodEvent]
        var deleted: [Tombstone]
        var device: String
    }

    /// Sends everything on the phone, gets the merged state back and merges it here.
    @MainActor
    static func sync(_ store: AppStore) async throws -> Int {
        let state = store.state()
        let body = try JSONEncoder().encode(Outgoing(entries: state.entries, events: state.events,
                                                     deleted: state.deleted, device: UIDevice.current.name))
        let data = try await request("api/sync", method: "POST", body: body)
        let remote: SyncState
        do {
            remote = try JSONDecoder().decode(SyncState.self, from: data)
        } catch {
            throw SyncError.server(tr("Your PC sent something unexpected. Update Moodlog on your PC to 2.0 or newer."))
        }
        return store.absorb(remote)
    }

    /// Sends Health lines (the same format as the shortcut) to /health.
    static func sendHealth(lines: [String]) async throws -> String {
        let body = try JSONSerialization.data(withJSONObject: ["data": lines.joined(separator: "\n")])
        let data = try await request("health", method: "POST", body: body)
        return (String(data: data, encoding: .utf8) ?? tr("Sent.")).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// Runs syncs: when the app opens, a couple of seconds after a change, and on request.
@MainActor
@Observable
final class SyncCoordinator {
    var status = tr("Not synced yet")
    var busy = false
    var healthStatus = ""

    @ObservationIgnored private var pending: Task<Void, Never>?

    func syncSoon(_ store: AppStore, delay: Double) {
        guard UserDefaults.standard.bool(forKey: "autoSync"), SyncClient.isConfigured else { return }
        pending?.cancel()
        pending = Task { [weak self] in
            if delay > 0 {
                try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            }
            if Task.isCancelled { return }
            await self?.run(store)
        }
    }

    func run(_ store: AppStore) async {
        if busy { return }
        busy = true
        defer { busy = false }
        do {
            let changes = try await SyncClient.sync(store)
            let when = Day.timeString(Date())
            status = changes > 0 ? trn("Synced at %2$@ · %1$@ change", "Synced at %2$@ · %1$@ changes", changes, when)
                : tr("Synced at %@", when)
            UserDefaults.standard.set(Date(), forKey: "lastSync")
            if UserDefaults.standard.bool(forKey: "sendHealth") {
                let last = UserDefaults.standard.object(forKey: "lastHealth") as? Date ?? .distantPast
                if Date().timeIntervalSince(last) > 3600 {
                    await sendHealth()
                }
            }
        } catch {
            status = error.localizedDescription
        }
    }

    func sendHealth() async {
        guard HealthSender.isAvailable else {
            healthStatus = tr("Apple Health isn't available on this device.")
            return
        }
        do {
            let lines = try await HealthSender.lines(days: 7)
            if lines.isEmpty {
                healthStatus = tr("No Health data found for the last 7 days (or access wasn't allowed in Settings → Health → Data Access).")
                return
            }
            let answer = try await SyncClient.sendHealth(lines: lines)
            UserDefaults.standard.set(Date(), forKey: "lastHealth")
            healthStatus = answer
        } catch {
            healthStatus = error.localizedDescription
        }
    }
}
