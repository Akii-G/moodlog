import os, importlib.machinery, importlib.util, datetime as dt, tempfile, zipfile, json, shutil
root = tempfile.mkdtemp()
os.environ["MOODLOG_CONFIG_DIR"] = os.path.join(root, "config")
os.environ.pop("MOODLOG_DATA_DIR", None)
os.environ["XDG_DATA_HOME"] = os.path.join(root, "xdg-data")
os.environ["MOODLOG_GRIDGETS_DIR"] = "/nonexistent"
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
D = dt.date
# default folder, then a chosen one through the config file
assert m.data_dir() == m.default_data_dir() and m.default_data_dir().startswith(root)
s = m.Store(); s.add(D(2026, 11, 1), "09:00", 4, "first", 3, 2); s.save()
ev = m.EventStore(); ev.add(D(2026, 11, 2), "medication", "New dose", ""); ev.save()
hs = m.HealthStore(); hs.values = {"steps": {D(2026, 11, 1): 4000}}; hs.save()
# backup
bk = os.path.join(root, "out", "b.zip"); names = m.make_backup(bk)
assert set(names) == {"moods.csv", "events.csv", "health.csv"}
with zipfile.ZipFile(bk) as z: assert m.MANIFEST in z.namelist() and json.loads(z.read(m.MANIFEST))["app"] == "Moodlog"
files, info = m.read_backup(bk); assert info["version"] == m.VERSION and b"New dose" in files["events.csv"]
# bad files are refused
open(os.path.join(root, "x.zip"), "w").write("nope")
for bad in (os.path.join(root, "x.zip"),):
    try: m.read_backup(bad); raise SystemExit("should refuse")
    except m.BackupError: pass
with zipfile.ZipFile(os.path.join(root, "empty.zip"), "w") as z: z.writestr("readme.txt", "hi")
try: m.read_backup(os.path.join(root, "empty.zip")); raise SystemExit("should refuse")
except m.BackupError as e: assert "no moods" in str(e)
with zipfile.ZipFile(os.path.join(root, "evil.zip"), "w") as z: z.writestr("../../moods.csv", "date\n"); z.writestr("moods.csv", "date,mood\n2026-11-05,1\n")
files, _ = m.read_backup(os.path.join(root, "evil.zip")); assert list(files) == ["moods.csv"]   # path tricks ignored
# change data, then restore: back to the backup, and a safety copy of the changed state exists
s.add(D(2026, 11, 3), "", 1, "later"); s.save(); m.EventStore().save()
safety = m.restore_backup(bk)
assert os.path.exists(safety) and "before-restore" in safety
assert D(2026, 11, 3) not in m.Store().days and m.Store().entries(D(2026, 11, 1)) == [["09:00", 4, "first", 3, 2]]
assert m.EventStore().items[0][2] == "New dose" and m.HealthStore().values["steps"][D(2026, 11, 1)] == 4000
with zipfile.ZipFile(safety) as z: assert b"later" in z.read("moods.csv")
# a backup without events -> restoring leaves no events (that was the state)
with zipfile.ZipFile(os.path.join(root, "old.zip"), "w") as z: z.writestr("moods.csv", "date,mood,note\n2026-10-01,3,old\n")
m.restore_backup(os.path.join(root, "old.zip")); assert m.EventStore().items == [] and m.Store().moods() == [(D(2026, 10, 1), 3)]
m.restore_backup(bk)
# merging another folder in: union, no duplicates
other = os.path.join(root, "pc2"); os.makedirs(other)
o = m.Store(os.path.join(other, "moods.csv")); o.add(D(2026, 11, 1), "09:00", 4, "first", 3, 2); o.add(D(2026, 11, 4), "20:00", 2, "pc2 only"); o.save()
oe = m.EventStore(os.path.join(other, "events.csv")); oe.add(D(2026, 11, 2), "medication", "New dose", ""); oe.add(D(2026, 11, 6), "life", "Trip", ""); oe.save()
dst = (m.Store(), m.EventStore(), m.HealthStore())
added = m.merge_folder_into(other, *dst)
assert added == 2 and len(dst[0].entries(D(2026, 11, 1))) == 1 and dst[0].entries(D(2026, 11, 4))[0][2] == "pc2 only"
assert [e[2] for e in dst[1].items] == ["New dose", "Trip"]
# conflict copies from Syncthing and Nextcloud are found and merged, then moved aside
dd = m.data_dir()
c1 = m.Store(os.path.join(dd, "moods.sync-conflict-20261118-101010-ABCDEFG.csv")); c1.add(D(2026, 11, 7), "08:00", 5, "from the laptop"); c1.save()
c2 = m.EventStore(os.path.join(dd, "events (conflicted copy 2026-11-18 101010).csv")); c2.add(D(2026, 11, 8), "health", "Check-up", ""); c2.save()
found = m.conflict_files(); assert [k for k, _p in found] == ["events", "moods"], found
st, eva, he = m.Store(), m.EventStore(), m.HealthStore()
n, added = m.merge_conflicts(st, eva, he)
assert n == 2 and added == 2 and m.conflict_files() == []
assert m.Store().entries(D(2026, 11, 7))[0][2] == "from the laptop" and any(e[2] == "Check-up" for e in m.EventStore().items)
assert len(os.listdir(os.path.join(dd, "backups", "merged-conflicts"))) == 2
# the config decides the folder; a missing folder falls back to the default
sync = os.path.join(root, "Sync", "Moodlog"); os.makedirs(sync)
m.save_config({"data_dir": sync}); assert m.data_dir() == sync
m.save_config({"data_dir": os.path.join(root, "gone")}); assert m.data_dir() == m.default_data_dir()
# Gridgets link state lives in the per-computer config folder
link = m.GridgetsLink(m.Store()); assert link.state_path.startswith(os.environ["MOODLOG_CONFIG_DIR"])
# outside change detection
s1 = m.Store(); assert not s1.changed_on_disk()
import time; time.sleep(0.05); s2 = m.Store(); s2.add(D(2026, 11, 9), "", 3, ""); s2.save()
assert s1.changed_on_disk() and not s2.changed_on_disk()
print("data OK")
