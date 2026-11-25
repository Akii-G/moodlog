"""Activity tags: kept in moods.csv, in the sync JSON, merged, searched and counted."""
import datetime as dt, json, os, tempfile, importlib.machinery, importlib.util
data = tempfile.mkdtemp(); cfgdir = tempfile.mkdtemp()
os.environ.update(MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=cfgdir, MOODLOG_LANG="en")
here = os.path.dirname(os.path.abspath(__file__))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(here, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

assert m.clean_activities("Walk; work, nonsense") == "work,walk"            # fixed order, junk dropped
assert m.clean_activities(["rest", "gaming"]) == "gaming,rest"
d = dt.date(2026, 11, 20)
s = m.Store()
e = s.add(d, "09:00", 4, "", activities="walk,friends")                       # only tags: still kept
p = s.add(d, "21:00", 2, "secret", private=True, activities="work")
s.save()
back = m.Store()
assert m.entry_activities(back.entries(d)[0]) == ["friends", "walk"]
assert m.activities_text(["friends", "walk"]) == "Friends, Walk"

doc = json.loads(json.dumps(m.stores_to_json(s, m.EventStore(""))))
assert doc["entries"][0]["activities"] == ["friends", "walk"] and "activities" in doc["entries"][1]
st, _ev = m.stores_from_json(doc)
assert m.entry_activities(st.entries(d)[0]) == ["friends", "walk"]
# an entry without activities (from an older app) has none, and none is sent
old = m.Store(""); old.add(d, "10:00", 3, "x")
assert "activities" not in m.stores_to_json(old, m.EventStore(""))["entries"][0]
assert m.entry_activities(old.entries(d)[0]) == []

# search finds the tag, counts skip private entries
assert [r[2] for r in m.search_results(s, m.EventStore(""), "walk")] == [e]
assert m.activity_counts(s) == [("friends", 1), ("walk", 1)]
print("activities ok")
