"""Questionnaires (PHQ-9, GAD-7): the store, merging with deletions, sync with the phone,
backups and the doctor report."""
import datetime as dt, json, os, tempfile, importlib.machinery, importlib.util
data = tempfile.mkdtemp(); cfgdir = tempfile.mkdtemp()
os.environ.update(MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=cfgdir, MOODLOG_LANG="en")
here = os.path.dirname(os.path.abspath(__file__))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(here, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

# The official texts: 9 and 7 questions, 4 answers each, in both languages.
for lang in ("en", "it"):
    t = m.SURVEY_TEXT[lang]
    assert len(t["phq9"]["items"]) == 9 and len(t["gad7"]["items"]) == 7
    assert all(len(t[k]["options"]) == 4 for k in m.SURVEY_KINDS) and len(t["difficulty_options"]) == 4
assert m.SURVEY_TEXT["it"]["gad7"]["options"][2] == "Per oltre la metà dei giorni"
assert m.SURVEY_TEXT["it"]["phq9"]["options"][2] == "Per più della metà dei giorni"

s = m.SurveyStore()
a = s.add("phq9", [1, 2, 0, 3, 1, 0, 2, 1, 0], 1, day=dt.date(2026, 11, 1), time="09:00")
b = s.add("gad7", [0, 1, 1, 2, 0, 3, 1], -1, day=dt.date(2026, 11, 1), time="09:05", private=True)
s.save()
s2 = m.SurveyStore()
assert [x["kind"] for x in s2.items] == ["phq9", "gad7"]
assert m.survey_score(s2.items[0]) == 10 and m.survey_max("phq9") == 27 and m.survey_max("gad7") == 21
assert s2.items[1]["private"] and not m.survey_worries(s2.items[0])
assert s2.due(dt.date(2026, 11, 14)) is False and s2.due(dt.date(2026, 11, 15)) is True

# Bad items from a phone are dropped one by one.
bad = [{"id": "x1", "date": "2026-11-02", "kind": "phq9", "answers": [0] * 8, "updated": "2026-11-02T10:00:00Z"},
       {"id": "x2", "date": "2026-11-02", "kind": "asrm", "answers": [0] * 5, "updated": "2026-11-02T10:00:00Z"},
       {"id": "x3", "date": "2026-11-02", "kind": "gad7", "answers": [0, 1, 2, 3, 4, 0, 0], "updated": "2026-11-02T10:00:00Z"},
       {"id": "x4", "date": "2026-11-02", "kind": "gad7", "answers": [0, 1, 2, 3, True, 0, 0], "updated": "2026-11-02T10:00:00Z"}]
assert m.SurveyStore("").absorb_json({"surveys": bad}) == 0

# Sync: the phone adds one (question 9 above 0) and deletes the GAD-7.
phone = {"entries": [], "events": [], "dreams": [],
         "surveys": [{"id": "p1", "date": "2026-11-15", "time": "21:00", "kind": "phq9",
                      "answers": [2, 2, 1, 1, 1, 1, 1, 1, 1], "difficulty": 2,
                      "updated": "2026-11-15T21:00:00Z"}],
         "deleted": [{"id": b["id"], "type": "survey", "deleted": "2099-01-01T00:00:00Z"}]}
state = m.sync_with_phone(json.dumps(phone))
assert state["changed"] == 2, state["changed"]
ids = [x["id"] for x in state["surveys"]]
assert ids == [a["id"], "p1"], ids
assert {"id": b["id"], "type": "survey", "deleted": "2099-01-01T00:00:00Z"} in state["deleted"]
assert m.survey_worries(m.SurveyStore().items[-1])
# Mood deletions and survey deletions don't get mixed up.
store, _ev = m.stores_from_json(phone)
assert not store.deleted
# The same again changes nothing; an older copy doesn't win.
assert m.sync_with_phone(json.dumps(phone))["changed"] == 0
old = dict(phone["surveys"][0], answers=[0] * 9, updated="2026-11-15T20:00:00Z")
assert m.sync_with_phone(json.dumps({"surveys": [old]}))["changed"] == 0

# Backups keep surveys.json.
zip_path = os.path.join(tempfile.mkdtemp(), "b.zip")
assert "surveys.json" in m.make_backup(zip_path)
files, _ = m.read_backup(zip_path)
assert b'"p1"' in files["surveys.json"]

# The report: private ones are left out.
pdf = os.path.join(tempfile.mkdtemp(), "r.pdf")
pages = m.export_report(pdf, m.Store(), m.EventStore(), m.HealthStore(), dt.date(2026, 11, 1),
                        dt.date(2026, 11, 30), surveys=m.SurveyStore())
assert pages >= 1 and os.path.getsize(pdf) > 1000
print("surveys ok")
