"""Feeling words: stored in moods.csv, synced with the phones, counted in the report.
Writing prompts: one question a day."""
import datetime as dt, json, os, tempfile, importlib.machinery, importlib.util
data = tempfile.mkdtemp(); cfgdir = tempfile.mkdtemp()
os.environ.update(MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=cfgdir, MOODLOG_LANG="en")
here = os.path.dirname(os.path.abspath(__file__))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(here, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

assert m.clean_feelings(["Tiredness", "joy", "nope"]) == "joy,tiredness"
assert m.clean_feelings("worry; calm") == "calm,worry"
s = m.Store()
d = dt.date(2026, 11, 20)
a = s.add(d, "09:00", 2, "", feelings="tiredness,worry")
b = s.add(d, "21:00", 0, "", feelings=["joy"])          # only a word: still an entry
c = s.add(d, "22:00", 3, "secret", private=True, feelings="tiredness")
s.save()
s2 = m.Store()
assert [m.entry_feelings(e) for e in s2.entries(d)] == [["worry", "tiredness"], ["joy"], ["tiredness"]]
counts = dict(m.feeling_counts(s2))
assert counts == {"tiredness": 1, "worry": 1, "joy": 1}, counts   # the private entry isn't counted

doc = m.stores_to_json(s2, m.EventStore(""))
by_time = {e["time"]: e for e in doc["entries"]}
assert by_time["09:00"]["feelings"] == ["worry", "tiredness"] and "feelings" not in m.stores_to_json(m.Store(""), m.EventStore(""))
phone = {"entries": [{"id": "ph1", "date": "2026-11-21", "time": "08:00", "mood": 4, "note": "",
                      "feelings": ["hope", "calm", "bogus"], "updated": "2026-11-21T07:00:00.000Z"}]}
state = m.sync_with_phone(json.dumps(phone))
assert state["changed"] == 1
got = [e for e in state["entries"] if e["id"] == "ph1"][0]
assert got["feelings"] == ["calm", "hope"], got
# an old moods.csv without the column still loads
old = os.path.join(tempfile.mkdtemp(), "moods.csv")
open(old, "w").write("date,time,mood,energy,anxiety,note,id,updated,deleted,private\n2026-11-01,09:00,3,,,,x1,2026-11-01T08:00:00.000Z,,\n")
assert m.entry_feelings(m.Store(old).entries(dt.date(2026, 11, 1))[0]) == []

assert m.prompt_for(d) != m.prompt_for(d, 1) and m.prompt_for(d) == m.prompt_for(d, len(m.PROMPTS))
pdf = os.path.join(tempfile.mkdtemp(), "r.pdf")
assert m.export_report(pdf, m.Store(), m.EventStore(), m.HealthStore(), dt.date(2026, 11, 1), dt.date(2026, 11, 30)) >= 1
print("feelings ok")
