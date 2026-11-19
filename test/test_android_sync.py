"""The Android app syncs with the real PC receiver: moods, events, dreams, deletions and
health data arrive on the PC, and the PC's entries arrive on the phone. Runs the Android
app's LiveSyncTest (android/) against a receiver started here with test data."""
import csv, json, os, subprocess, sys, tempfile, time, urllib.request
import importlib.machinery, importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "moodlog")
data = tempfile.mkdtemp()
os.environ["MOODLOG_DATA_DIR"] = data; os.environ["MOODLOG_LANG"] = "en"
loader = importlib.machinery.SourceFileLoader("moodlog", SRC)
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

# one entry made on the PC
m.sync_with_phone(json.dumps({"entries": [{"id": "pc1", "date": "2026-11-16", "time": "20:00", "mood": 2,
    "energy": 0, "anxiety": 0, "note": "from the pc", "updated": "2026-11-16T19:00:00.000Z"}],
    "events": [], "deleted": []}))
token, port = "android-test-token-0123", 18798
json.dump({"token": token, "port": port}, open(os.path.join(data, "sync.json"), "w"))
proc = subprocess.Popen([sys.executable, SRC, "--sync-server"], env=dict(os.environ),
                        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
try:
    for _ in range(50):
        try:
            urllib.request.urlopen("http://127.0.0.1:%d/" % port, timeout=1)
        except urllib.error.HTTPError:
            break
        except OSError:
            time.sleep(0.2)
    env = dict(os.environ, MOODLOG_TEST_SERVER="http://127.0.0.1:%d" % port, MOODLOG_TEST_TOKEN=token)
    run = subprocess.run(["./gradlew", "-q", "testDebugUnitTest", "--tests", "io.github.akiig.moodlog.LiveSyncTest",
                          "--rerun-tasks"] if "--rerun" in sys.argv else
                         ["./gradlew", "-q", "testDebugUnitTest", "--tests", "io.github.akiig.moodlog.LiveSyncTest"],
                         cwd=os.path.join(HERE, "..", "android"), env=env, capture_output=True, text=True)
    if run.returncode != 0:
        print(run.stdout[-3000:], run.stderr[-3000:]); raise SystemExit("LiveSyncTest failed")
    report = open(os.path.join(HERE, "..", "android", "app", "build", "test-results", "testDebugUnitTest",
                               "TEST-io.github.akiig.moodlog.LiveSyncTest.xml")).read()
    assert 'skipped="0"' in report and 'failures="0"' in report, report[:600]

    moods = list(csv.DictReader(open(os.path.join(data, "moods.csv"), encoding="utf-8")))
    notes = {r["note"]: r for r in moods}
    assert "from android è" in notes and notes["from android è"]["date"] == "2026-11-18", moods
    assert "to delete" not in notes and any(r["deleted"] for r in moods), "deletion did not reach the PC"
    events = open(os.path.join(data, "events.csv"), encoding="utf-8").read()
    assert "New dose" in events
    dreams = open(os.path.join(data, "dreams.csv"), encoding="utf-8").read()
    assert "a dream from android" in dreams
    health = open(os.path.join(data, "health.csv"), encoding="utf-8").read()
    assert "8234" in health and "resting_hr" in health and "sleep_hours" in health, health
    assert "Encrypted." in open(os.path.join(data, "sync-last.txt")).read()
    log = proc.stderr.read1(100000).decode() if proc.poll() is None else ""
    assert token not in log
finally:
    proc.terminate()
print("ANDROID SYNC OK")
