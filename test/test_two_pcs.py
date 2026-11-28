"""One phone, two PCs: the Android app syncs with both receivers in turn, and what was
written on one PC reaches the other through the phone. Runs android's TwoPCsTest against
two receivers started here, then checks both PCs' files."""
import csv, json, os, subprocess, sys, tempfile, time, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "moodlog")
SEED = """
import importlib.machinery, importlib.util, json, sys
loader = importlib.machinery.SourceFileLoader("moodlog", sys.argv[1])
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
m.sync_with_phone(json.dumps({"entries": [{"id": sys.argv[2], "date": "2026-11-16", "time": "20:00", "mood": 2,
    "energy": 0, "anxiety": 0, "note": "from " + sys.argv[2], "updated": "2026-11-16T19:00:00.000Z"}],
    "events": [], "deleted": []}))
"""
pcs, procs = [], []
try:
    for name, port in (("linux", 18801), ("mac", 18802)):
        data = tempfile.mkdtemp()
        env = dict(os.environ, MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=tempfile.mkdtemp(), MOODLOG_LANG="en")
        subprocess.run([sys.executable, "-c", SEED, os.path.abspath(SRC), name], env=env, check=True, cwd=data)
        token = "two-pcs-token-%s-0123" % name
        json.dump({"token": token, "port": port}, open(os.path.join(data, "sync.json"), "w"))
        procs.append(subprocess.Popen([sys.executable, SRC, "--sync-server"], env=env,
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        pcs.append((name, port, token, data))
    for _, port, _, _ in pcs:
        for _ in range(50):
            try:
                urllib.request.urlopen("http://127.0.0.1:%d/" % port, timeout=1)
            except urllib.error.HTTPError:
                break
            except OSError:
                time.sleep(0.2)
    env = dict(os.environ, MOODLOG_TEST_PCS=json.dumps([{"name": n, "server": "http://127.0.0.1:%d" % p, "token": t}
                                                        for n, p, t, _ in pcs]))
    cmd = ["./gradlew", "-q", "testDebugUnitTest", "--tests", "io.github.akiig.moodlog.TwoPCsTest"]
    if "--rerun" in sys.argv:
        cmd.append("--rerun-tasks")
    run = subprocess.run(cmd, cwd=os.path.join(HERE, "..", "android"), env=env, capture_output=True, text=True)
    if run.returncode != 0:
        print(run.stdout[-3000:], run.stderr[-3000:]); raise SystemExit("TwoPCsTest failed")
    report = open(os.path.join(HERE, "..", "android", "app", "build", "test-results", "testDebugUnitTest",
                               "TEST-io.github.akiig.moodlog.TwoPCsTest.xml")).read()
    assert 'skipped="0"' in report and 'failures="0"' in report, report[:600]
    for name, _, _, data in pcs:
        notes = {r["note"] for r in csv.DictReader(open(os.path.join(data, "moods.csv"), encoding="utf-8"))}
        assert {"from linux", "from mac", "from the phone"} <= notes, (name, notes)
finally:
    for p in procs:
        p.terminate()
print("TWO PCS OK")
