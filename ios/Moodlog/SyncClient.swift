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
            return tr("Wrong token. Copy it again from Sottovoce on your PC (Phone sync).")
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

    /// Sends `payload` encrypted to `path` (e.g. "/api/e/sync") and returns the decrypted reply.
    /// The token is only used as the key: it is never sent.
    static func request(_ path: String, payload: Data) async throws -> Data {
        let url = try baseURL().appendingPathComponent(String(path.dropFirst()))
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 20
        req.httpBody = try SyncCrypto.seal(payload, token: token, path: path)
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let result: (Data, URLResponse)
        do {
            result = try await URLSession.shared.data(for: req)
        } catch let e as URLError where e.code == .cancelled {
            throw CancellationError()
        } catch {
            throw SyncError.unreachable(error.localizedDescription)
        }
        let data = result.0
        guard let http = result.1 as? HTTPURLResponse else { throw SyncError.server(tr("No answer from your PC.")) }
        if http.statusCode == 401 { throw SyncError.unauthorized }
        if http.statusCode == 404 {
            throw SyncError.server(tr("Your PC runs an older Sottovoce. Update it to 2.5 or newer: syncing is now encrypted."))
        }
        if !(200..<300).contains(http.statusCode) {
            if let doc = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let message = doc["error"] as? String {
                throw SyncError.server(message)
            }
            throw SyncError.server(tr("Your PC answered with error %@.", String(http.statusCode)))
        }
        do {
            return try SyncCrypto.open(data, token: token, path: path)
        } catch {
            throw SyncError.unauthorized
        }
    }

    /// Checks the address and token. Returns e.g. "Connected to Sottovoce 2.5.0".
    static func ping() async throws -> String {
        let data = try await request("/api/e/ping", payload: Data("{}".utf8))
        guard let doc = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let version = doc["version"] as? String else {
            throw SyncError.server(tr("Your PC sent something unexpected. Update Sottovoce on your PC to 2.5 or newer."))
        }
        return tr("Connected to Sottovoce %@ on your PC (encrypted)", version)
    }

    private struct Outgoing: Codable {
        var entries: [Entry]
        var events: [MoodEvent]
        var deleted: [Tombstone]
        var dreams: [Dream]
        var surveys: [Survey]   // an older PC ignores them
        var device: String
    }

    /// Sends everything on the phone, gets the merged state back and merges it here.
    @MainActor
    static func sync(_ store: AppStore) async throws -> Int {
        let state = store.state()
        let body = try JSONEncoder().encode(Outgoing(entries: state.entries, events: state.events,
                                                     deleted: state.deleted, dreams: state.dreams ?? [],
                                                     surveys: state.surveys ?? [], device: UIDevice.current.name))
        let data = try await request("/api/e/sync", payload: body)
        let remote: SyncState
        do {
            remote = try JSONDecoder().decode(SyncState.self, from: data)
        } catch {
            throw SyncError.server(tr("Your PC sent something unexpected. Update Sottovoce on your PC to 2.5 or newer."))
        }
        return store.absorb(remote)
    }

    /// Sends Health lines (the same format as the shortcut), encrypted.
    static func sendHealth(lines: [String]) async throws -> String {
        let body = try JSONSerialization.data(withJSONObject: ["lines": lines.joined(separator: "\n")])
        let data = try await request("/api/e/health", payload: body)
        let doc = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        return ((doc?["message"] as? String) ?? tr("Sent.")).trimmingCharacters(in: .whitespacesAndNewlines)
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
    @ObservationIgnored private var again = false

    func syncSoon(_ store: AppStore, delay: Double) {
        guard UserDefaults.standard.bool(forKey: "autoSync"), SyncClient.isConfigured else { return }
        pending?.cancel()
        pending = Task { [weak self] in
            if delay > 0 {
                try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            }
            if Task.isCancelled { return }
            // The sync runs in a task of its own: a newer syncSoon only cancels the wait,
            // never a sync that is already talking to the PC.
            Task { await self?.run(store) }
        }
    }

    func run(_ store: AppStore) async {
        if busy {           // one at a time; sync once more when this one ends
            again = true
            return
        }
        busy = true
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
        } catch is CancellationError {
            // stopped on purpose: keep the last status instead of showing an error
        } catch {
            status = error.localizedDescription
        }
        busy = false
        if again {
            again = false
            await run(store)
        }
    }

    func sendHealth() async {
        guard HealthSender.isAvailable else {
            healthStatus = tr("Apple Health isn't available on this device.")
            return
        }
        do {
            let meds = await Medications.lines()
            let lines = try await HealthSender.lines(days: 7) + meds.lines
            if lines.isEmpty {
                healthStatus = tr("No Health data found for the last 7 days (or access wasn't allowed in Settings → Health → Data Access).")
                return
            }
            let answer = try await SyncClient.sendHealth(lines: lines)
            UserDefaults.standard.set(Date(), forKey: "lastHealth")
            if meds.all { UserDefaults.standard.set(true, forKey: "medicationsSentAll") }
            healthStatus = answer
        } catch {
            healthStatus = error.localizedDescription
        }
    }
}
