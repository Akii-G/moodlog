"""Dream journal: storage, merging, deletions, phone sync, backups and conflict copies."""
import datetime as dt, importlib.machinery, importlib.util, json, os, sys, tempfile, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
d = tempfile.mkdtemp()
os.environ["MOODLOG_DATA_DIR"] = d
os.environ["MOODLOG_LANG"] = "en"
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(HERE, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader)
m = importlib.util.module_from_spec(spec); loader.exec_module(m)

day = dt.date(2026, 11, 18)
ds = m.DreamStore()
assert ds.path == os.path.join(d, "dreams.csv")
assert m.DreamStore("").path.endswith("dreams.csv"), "an in-memory store never points at events.csv"
a = ds.add(day, "disturbing", m.clean_tags(["vivid", "nightmare", "bogus"]), "Falling, then\nthe sea")
b = ds.add(day + dt.timedelta(days=1), "", "forgotten", "")
ds.save()

# round trip, tags in a fixed order, multi-line text kept
again = m.DreamStore()
assert [x[:4] for x in again.items] == [[day, "disturbing", "nightmare,vivid", "Falling, then\nthe sea"],
                                        [day + dt.timedelta(days=1), "", "forgotten", ""]]
assert again.is_nightmare(again.items[0]) and not again.is_nightmare(again.items[1])
assert m.dream_details(again.items[0]) == "Disturbing · Nightmare, Vivid"
assert m.dream_title(again.items[0]) == "Falling, then the sea"
assert m.dream_title(again.items[1]) == "Didn't remember"
assert open(ds.path).readline().strip() == "date,feeling,tags,text,id,updated,deleted,private"

# a row with nothing in it is not a dream
open(os.path.join(d, "x.csv"), "w").write("date,feeling,tags,text\n2026-11-01,,,\n2026-11-02,weird,,dreamt\n")
x = m.DreamStore(os.path.join(d, "x.csv"))
assert [(i[1], i[3]) for i in x.items] == [("", "dreamt")]

# newest change wins, deletions win over older changes
other = m.DreamStore(""); other.items = [list(i) for i in again.items]; other.deleted = {}
other.update(other.items[0], day, "pleasant", "lucid", "Flying")
other.items[0][5] = "2099-01-01T00:00:00.000Z"
assert ds.absorb(other) == 1 and ds.items[0][1:4] == ["pleasant", "lucid", "Flying"]
ds.remove(ds.items[1]); ds.save()
assert len(m.DreamStore().items) == 1 and b[4] in m.DreamStore().deleted

# the phone sync: dreams in and out, older phones (no "dreams") leave them alone
state = m.sync_with_phone(json.dumps({"entries": [], "events": [], "deleted": []}))
assert len(state["dreams"]) == 1 and state["changed"] == 0
assert {"id": b[4], "type": "dream", "deleted": m.DreamStore().deleted[b[4]]} in state["deleted"]
phone = {"entries": [], "events": [], "deleted": [], "dreams": [
    {"id": "phonedream1", "date": "2026-11-17", "feeling": "neutral", "tags": ["recurring"],
     "text": "The old school again", "updated": "2026-11-17T07:00:00.000Z"},
    {"id": "bad", "date": "not a date", "text": "x", "updated": "2026-11-17T07:00:00.000Z"}]}
state = m.sync_with_phone(json.dumps(phone))
assert state["changed"] == 1 and len(state["dreams"]) == 2
assert [x for x in state["dreams"] if x["id"] == "phonedream1"][0]["tags"] == ["recurring"]
gone = {"entries": [], "events": [], "dreams": [], "deleted": [
    {"id": "phonedream1", "type": "dream", "deleted": "2026-11-18T09:00:00.000Z"}]}
state = m.sync_with_phone(json.dumps(gone))
assert [x["id"] for x in state["dreams"]] == [a[4]]

# stats
store = m.Store()
store.add(day, "08:00", 2, "", 0, 0); store.add(dt.date(2026, 11, 16), "08:00", 4, "", 0, 0); store.save()
ds = m.DreamStore(); ds.add(dt.date(2026, 11, 16), "pleasant", "", "Sunny"); ds.save()
st = m.dream_stats(m.Store(), m.DreamStore())
assert st["nights"] == 2 and st["dreams"] == 2 and st["nightmares"] == 0 and st["pleasant"] == 2, st

# backups carry dreams.csv; conflict copies are merged
zpath = os.path.join(d, "b.zip")
assert "dreams.csv" in m.make_backup(zpath)
files, _ = m.read_backup(zpath)
assert b"Sunny" in files["dreams.csv"]
c = m.DreamStore(os.path.join(d, "dreams.sync-conflict-20261119-aaa.csv"))
c.items = []; c.deleted = {}; c.add(dt.date(2026, 11, 10), "", "vivid", "From the other PC"); c.save()
n, added = m.merge_conflicts(m.Store(), m.EventStore(), m.HealthStore())
assert n == 1 and added == 1 and any(i[3] == "From the other PC" for i in m.DreamStore().items)
print("DREAMS OK")
