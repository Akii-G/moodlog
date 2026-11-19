// Checks SyncCrypto.swift against the values test/test_encrypted_sync.py checks on the PC,
// so the iPhone and the PC are sure to encrypt the same way. Run by the iOS workflow:
//   swiftc -o crypto-check ios/Moodlog/SyncCrypto.swift ios/CryptoCheck/main.swift && ./crypto-check
import CryptoKit
import Foundation

func hex(_ data: Data) -> String { return data.map { String(format: "%02x", $0) }.joined() }

let key = SyncCrypto.key("test-token-0123456789")
let keyHex = hex(key.withUnsafeBytes { Data($0) })
guard keyHex == "3f50a3ff4c7099fe930d06eb96376ad1f877327018cb8011e603d99f54741887" else {
    print("key mismatch: \(keyHex)"); exit(1)
}
let box = try AES.GCM.seal(Data("{\"a\":1}".utf8), using: key, nonce: try AES.GCM.Nonce(data: Data(count: 12)),
                           authenticating: SyncCrypto.aad("/api/e/ping", reply: false))
let ct = hex(box.ciphertext + box.tag)
guard ct == "1c34af008a5dc72ec3fd26e58f41e66bbf3296134970b2" else {
    print("ciphertext mismatch: \(ct)"); exit(1)
}
// a full round trip the way the PC answers (reply direction)
let reply = try AES.GCM.seal(Data("{\"ok\":true}".utf8), using: key, nonce: AES.GCM.Nonce(),
                             authenticating: SyncCrypto.aad("/api/e/sync", reply: true))
let doc: [String: Any] = ["v": 1, "n": reply.nonce.withUnsafeBytes { Data($0) }.base64EncodedString(),
                          "c": (reply.ciphertext + reply.tag).base64EncodedString()]
let opened = try SyncCrypto.open(try JSONSerialization.data(withJSONObject: doc),
                                 token: "test-token-0123456789", path: "/api/e/sync")
guard String(data: opened, encoding: .utf8) == "{\"ok\":true}" else { print("round trip failed"); exit(1) }
print("crypto vectors OK")
