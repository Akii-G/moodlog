"""Encrypted phone sync: AES-256-GCM with a key from the token. The token never travels;
a wrong token, a tampered message or one replayed on another endpoint is refused.
Also checks fixed test vectors, so the iPhone app (CryptoKit) can be checked against them."""
import base64, json, os, subprocess, sys, tempfile, time, urllib.error, urllib.request
import importlib.machinery, importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "moodlog")
data = tempfile.mkdtemp()
os.environ["MOODLOG_DATA_DIR"] = data; os.environ["MOODLOG_LANG"] = "en"
loader = importlib.machinery.SourceFileLoader("moodlog", SRC)
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

# fixed vectors: HKDF-SHA256(token, salt "moodlog-sync", info "moodlog sync v1") -> key
# (ios/CryptoCheck/main.swift checks the same two values with CryptoKit)
VECTOR_KEY = "3f50a3ff4c7099fe930d06eb96376ad1f877327018cb8011e603d99f54741887"
VECTOR_CT = "1c34af008a5dc72ec3fd26e58f41e66bbf3296134970b2"
key = m.sync_key("test-token-0123456789")
assert key.hex() == VECTOR_KEY, key.hex()
import hashlib, hmac                       # HKDF (RFC 5869) by hand, as a second opinion
prk = hmac.new(b"moodlog-sync", b"test-token-0123456789", hashlib.sha256).digest()
okm = hmac.new(prk, b"moodlog sync v1" + b"\x01", hashlib.sha256).digest()
assert okm.hex() == VECTOR_KEY
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
assert AESGCM(key).encrypt(bytes(12), b'{"a":1}', m.sync_aad("/api/e/ping", False)).hex() == VECTOR_CT

# round trip, and what must be refused
msg = m.seal("tok-aaaaaaaaaaaaaaaa", "/api/e/sync", {"x": "è"})
assert m.unseal("tok-aaaaaaaaaaaaaaaa", "/api/e/sync", msg) == {"x": "è"}
for args in (("tok-bbbbbbbbbbbbbbbb", "/api/e/sync", msg),            # wrong token
             ("tok-aaaaaaaaaaaaaaaa", "/api/e/health", msg),          # other endpoint
             ("tok-aaaaaaaaaaaaaaaa", "/api/e/sync", msg, True)):     # as a reply
    try:
        m.unseal(*args); raise AssertionError("accepted: %r" % (args[:2],))
    except ValueError:
        pass
doc = json.loads(msg); raw = bytearray(base64.b64decode(doc["c"])); raw[0] ^= 1
doc["c"] = base64.b64encode(bytes(raw)).decode()
try:
    m.unseal("tok-aaaaaaaaaaaaaaaa", "/api/e/sync", json.dumps(doc)); raise AssertionError("tampered")
except ValueError:
    pass
assert "tok-aaaaaaaaaaaaaaaa" not in msg.decode() and "è" not in msg.decode()

# the real receiver
token, port = "receiver-test-token-0123", 18797
json.dump({"token": token, "port": port}, open(os.path.join(data, "sync.json"), "w"))
proc = subprocess.Popen([sys.executable, SRC, "--sync-server"], env=dict(os.environ),
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def post(path, body, headers=None):
    req = urllib.request.Request("http://127.0.0.1:%d%s" % (port, path), data=body,
                                 headers=headers or {"Content-Type": "application/json"})
    try:
        r = urllib.request.urlopen(req, timeout=5); return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


try:
    for _ in range(40):
        try:
            post("/api/e/ping", b"{}"); break
        except OSError:
            time.sleep(0.25)
    code, body = post("/api/e/ping", m.seal(token, "/api/e/ping", {}))
    assert code == 200 and m.unseal(token, "/api/e/ping", body, reply=True)["version"] == m.VERSION
    code, body = post("/api/e/ping", m.seal("wrong-token-wrong-token", "/api/e/ping", {}))
    assert code == 401
    entry = {"id": "e1", "date": "2026-11-18", "time": "09:00", "mood": 4, "energy": 0, "anxiety": 0,
             "note": "secret note", "updated": "2026-11-18T09:00:00.000Z"}
    code, body = post("/api/e/sync", m.seal(token, "/api/e/sync",
                                              {"entries": [entry], "events": [], "deleted": [], "dreams": []}))
    state = m.unseal(token, "/api/e/sync", body, reply=True)
    assert code == 200 and state["changed"] == 1 and b"secret note" not in body
    code, body = post("/api/e/health", m.seal(token, "/api/e/health",
                                                {"lines": "steps;2026-11-18;8000"}))
    assert code == 200 and m.unseal(token, "/api/e/health", body, reply=True)["stored"]
    assert "Encrypted." in open(os.path.join(data, "sync-last.txt")).read()
    # changing the token works at once, without restarting the receiver
    new = m.new_sync_token()
    assert post("/api/e/ping", m.seal(token, "/api/e/ping", {}))[0] == 401
    assert post("/api/e/ping", m.seal(new, "/api/e/ping", {}))[0] == 200
    # the Shortcut's plain request still works, and is reported as not encrypted
    code, _ = post("/health", b"steps;2026-11-17;5000", {"Authorization": "Bearer " + new})
    assert code == 200 and "Not encrypted" in open(os.path.join(data, "sync-last.txt")).read()
finally:
    proc.terminate(); proc.wait(timeout=5)
print("ENCRYPTED SYNC OK")
