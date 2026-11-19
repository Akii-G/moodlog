import CryptoKit
import Foundation

/// Encrypted sync with Moodlog on the PC (2.5 and newer).
///
/// AES-256-GCM with a key derived from the shared token (HKDF-SHA256), so the token itself
/// never crosses the network. A message is JSON {"v": 1, "n": base64 nonce, "c": base64
/// ciphertext+tag}; the additional data binds it to its endpoint and direction. This must
/// match seal()/unseal() in src/moodlog; ios/CryptoCheck checks both against fixed values.
enum SyncCrypto {
    enum Failure: Error {
        case unreadable
    }

    static func key(_ token: String) -> SymmetricKey {
        return HKDF<SHA256>.deriveKey(inputKeyMaterial: SymmetricKey(data: Data(token.utf8)),
                                      salt: Data("moodlog-sync".utf8),
                                      info: Data("moodlog sync v1".utf8),
                                      outputByteCount: 32)
    }

    static func aad(_ path: String, reply: Bool) -> Data {
        return Data("moodlog/v1 \(reply ? "reply" : "request") \(path)".utf8)
    }

    /// A request for `path` (e.g. "/api/e/sync").
    static func seal(_ plain: Data, token: String, path: String) throws -> Data {
        let box = try AES.GCM.seal(plain, using: key(token), nonce: AES.GCM.Nonce(),
                                   authenticating: aad(path, reply: false))
        let nonce = box.nonce.withUnsafeBytes { Data($0) }
        let doc: [String: Any] = ["v": 1, "n": nonce.base64EncodedString(),
                                  "c": (box.ciphertext + box.tag).base64EncodedString()]
        return try JSONSerialization.data(withJSONObject: doc)
    }

    /// The PC's reply to a request for `path`.
    static func open(_ body: Data, token: String, path: String) throws -> Data {
        guard let doc = try? JSONSerialization.jsonObject(with: body) as? [String: Any],
              (doc["v"] as? Int) == 1,
              let n = (doc["n"] as? String).flatMap({ Data(base64Encoded: $0) }), n.count == 12,
              let c = (doc["c"] as? String).flatMap({ Data(base64Encoded: $0) }), c.count >= 16
        else { throw Failure.unreadable }
        do {
            let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: n),
                                            ciphertext: c.prefix(c.count - 16), tag: c.suffix(16))
            return try AES.GCM.open(box, using: key(token), authenticating: aad(path, reply: true))
        } catch {
            throw Failure.unreadable
        }
    }
}
