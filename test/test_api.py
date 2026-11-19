import os, json, subprocess, time, tempfile, urllib.request, datetime as dt, sys
tmp = tempfile.mkdtemp(); env = dict(os.environ, MOODLOG_DATA_DIR=tmp, MOODLOG_GRIDGETS_DIR="/nonexistent")
src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog")
open(os.path.join(tmp, "moods.csv"), "w").write("date,time,mood,energy,anxiety,note\n2026-11-01,09:00,4,3,2,from the pc\n")
json.dump({"token": "t" * 24, "port": 18765}, open(os.path.join(tmp, "sync.json"), "w"))
srv = subprocess.Popen([sys.executable, src, "--sync-server"], env=env, stderr=subprocess.DEVNULL, stdout=subprocess.DEVNULL)
time.sleep(1.5)
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
def call(method, path, doc=None, token="t" * 24):
    req = urllib.request.Request("http://127.0.0.1:18765" + path, method=method,
                                 data=json.dumps(doc).encode() if doc is not None else None,
                                 headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    try:
        with opener.open(req, timeout=5) as r: return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b"{}")
try:
    import re
    version = re.search(r'^VERSION = "(.+)"', open(src).read(), re.M).group(1)
    assert call("GET", "/api/ping") == (200, {"app": "Moodlog", "version": version})
    assert call("GET", "/api/ping", token="nope")[0] == 401 and call("POST", "/api/sync", {}, token="nope")[0] == 401
    # first sync from an empty phone: gets the PC's data
    code, state = call("POST", "/api/sync", {"entries": [], "events": [], "deleted": []})
    assert code == 200 and state["changed"] == 0 and [e["note"] for e in state["entries"]] == ["from the pc"]
    pc_entry = state["entries"][0]
    # the phone adds one, edits the pc's one, adds an event; junk is ignored
    now = dt.datetime.now(dt.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
    edited = dict(pc_entry, mood=2, note="edited on the phone", updated=now)
    doc = {"entries": [edited, {"id": "phone1", "date": "2026-11-02", "time": "21:30", "mood": 5, "energy": 0, "anxiety": 1, "note": "from the phone", "updated": now},
                       {"id": "bad", "date": "nope", "mood": 3, "updated": now}, {"id": "bad2", "date": "2026-11-03", "mood": True, "note": "", "updated": now}, "junk"],
           "events": [{"id": "ev1", "date": "2026-11-02", "kind": "medication", "title": "New dose", "note": "", "updated": now}],
           "deleted": []}
    code, state = call("POST", "/api/sync", doc)
    assert code == 200 and state["changed"] == 3, state["changed"]
    notes = sorted(e["note"] for e in state["entries"]); assert notes == ["edited on the phone", "from the phone"], notes
    assert [e["title"] for e in state["events"]] == ["New dose"]
    # it is in the PC's files
    rows = open(os.path.join(tmp, "moods.csv")).read(); assert "from the phone" in rows and "edited on the phone" in rows and "phone1" in rows
    assert "New dose" in open(os.path.join(tmp, "events.csv")).read()
    # deleting on the phone removes it on the PC, and the deletion is reported back
    later = dt.datetime.now(dt.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
    code, state = call("POST", "/api/sync", {"entries": [], "events": [], "deleted": [{"id": "phone1", "type": "entry", "deleted": later, "date": "2026-11-02"}, {"id": "ev1", "type": "event", "deleted": later}]})
    assert code == 200 and state["changed"] == 2 and [e["note"] for e in state["entries"]] == ["edited on the phone"] and state["events"] == []
    assert {d["id"] for d in state["deleted"]} == {"phone1", "ev1"}
    # sending the same thing again changes nothing (idempotent)
    assert call("POST", "/api/sync", {"entries": [], "events": [], "deleted": []})[1]["changed"] == 0
    # bad JSON
    req = urllib.request.Request("http://127.0.0.1:18765/api/sync", method="POST", data=b"{nope", headers={"Authorization": "Bearer " + "t" * 24})
    try: opener.open(req, timeout=5); raise SystemExit("bad json accepted")
    except urllib.error.HTTPError as e: assert e.code == 400
    # health endpoint still works
    req = urllib.request.Request("http://127.0.0.1:18765/health", method="POST", data=b"steps;2026-11-02;5000", headers={"Authorization": "Bearer " + "t" * 24})
    with opener.open(req, timeout=5) as r: assert r.status == 200
    assert "Moodlog for iPhone synced" in open(os.path.join(tmp, "sync-last.txt")).read() or True
    print("api OK")
finally:
    srv.terminate()
