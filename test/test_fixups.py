"""Leaving short nights out, and one medicine logged under several names."""
import datetime as dt, json, os, runpy, sys, tempfile

root = tempfile.mkdtemp()
os.environ["MOODLOG_DATA_DIR"] = os.path.join(root, "moodlog")
os.environ["DOSE_DATA_DIR"] = os.path.join(root, "dose")
os.makedirs(os.environ["MOODLOG_DATA_DIR"]); os.makedirs(os.environ["DOSE_DATA_DIR"])
ns = runpy.run_path(os.path.join(os.path.dirname(__file__), "..", "src", "moodlog"), run_name="fixups")
D = dt.date

# ---- nights
with open(os.path.join(os.environ["MOODLOG_DATA_DIR"], "health.csv"), "w") as f:
    f.write("date,metric,value\n2026-11-10,sleep_hours,8\n2026-11-11,sleep_hours,0.8\n2026-11-12,sleep_hours,7.5\n")
h = ns["HealthStore"]()
assert [n[0] for n in h.short_nights()] == [D(2026, 11, 11)]
h.delete_night(D(2026, 11, 11))
h = ns["HealthStore"]()
assert D(2026, 11, 11) not in h.values["sleep_hours"] and h.short_nights() == []
assert "2026-11-11" not in open(h.path).read()                        # gone from the file
h.merge({"sleep_hours": {D(2026, 11, 11): 1.0, D(2026, 11, 13): 7}})   # a sync can't bring it back
h.save()
h = ns["HealthStore"]()
assert D(2026, 11, 11) not in h.values["sleep_hours"] and D(2026, 11, 13) in h.values["sleep_hours"]
# a night deleted on a phone arrives with the sync and goes away here too
C = ns["Corrections"]
c = C(); assert c.absorb_json({"nights": {"2026-11-13": "2026-11-26T18:00:00Z"}}) == 1; c.save()
assert D(2026, 11, 13) not in ns["HealthStore"]().values["sleep_hours"]
print("short nights ok")

# ---- names
doses = [{"date": "2026-11-%02d" % d, "time": "08:00", "status": "taken", "name": n, "amount": 1, "unit": ""}
         for d, n in [(20, "Aripiprazolo 10mg"), (21, "Aripiprazolo 10mg"), (22, "Aripiprazolo 15mg"),
                      (23, "Aripiprazolo 15mg"), (23, "Etizolam")]]
json.dump({"medications": {}, "doses": doses},
          open(os.path.join(os.environ["MOODLOG_DATA_DIR"], "medications.json"), "w"))
json.dump({"medicines": [{"id": "a", "name": "Aripiprazolo Teva", "strength": "15 mg", "unit": "pill",
                          "amount": 1, "ingredient": "Aripiprazolo",
                          "schedule": {"kind": "daily", "times": ["08:00"], "start": "2026-11-23"}},
                         {"id": "d", "name": "Depas", "strength": "1 mg", "unit": "pill", "amount": 1,
                          "ingredient": "Etizolam", "schedule": {"kind": "needed"}}],
           "doses": [{"med": "a", "day": "2026-11-%02d" % d, "time": "08:00", "status": "taken", "at": "08:00"}
                     for d in (23, 24)] + [{"med": "d", "day": "2026-11-24", "time": "", "status": "taken", "at": "16:00"}]},
          open(os.path.join(os.environ["DOSE_DATA_DIR"], "dose.json"), "w"))
m = ns["Medication"]()
groups = m.suggestions()
assert ["Aripiprazolo 10mg", "Aripiprazolo 15mg", "Aripiprazolo Teva 15 mg"] in groups, groups
assert ["Depas 1 mg", "Etizolam"] in groups, groups   # by Dose's active ingredient
m.combine(groups[0], "Aripiprazolo")
m = ns["Medication"]()                                 # kept on disk
S, E = D(2026, 11, 20), D(2026, 11, 24)
rows = {r[0]: r for r in m.summary(S, E, now=dt.datetime(2026, 11, 24, 23))}
assert rows["Aripiprazolo"][1] == 5, rows               # 23 Nov in both apps counts once
assert set(rows) == {"Aripiprazolo", "Etizolam", "Depas 1 mg"}, rows   # old names gone
feed = ns["dose_to_json"](m.dose)
assert {d["name"] for d in feed["doses"]} == {"Aripiprazolo", "Depas 1 mg"}       # phones get the new name
assert any(x["ingredient"] == "Etizolam" for x in feed["medicines"])
# renames merge by time: a newer undo from a phone wins, an older one doesn't
c = C(); stamp = c.names["Aripiprazolo 10mg"]["updated"]
assert c.absorb_json({"names": {"Aripiprazolo 10mg": {"to": "", "updated": "2000-01-01T00:00:00Z"}}}) == 0
assert c.renames()["Aripiprazolo 10mg"] == "Aripiprazolo"
assert not any(g for g in m.suggestions() if "Aripiprazolo 10mg" in g)
m.separate("Aripiprazolo")
assert "Aripiprazolo 10mg" in {r[0] for r in m.summary(S, E)}
print("medicine names ok")

# ---- the sync carries corrections both ways
state = ns["sync_with_phone"](json.dumps({"entries": [], "events": [], "deleted": [],
                                          "corrections": {"nights": {"2026-11-10": "2026-11-26T19:00:00Z"},
                                                          "names": {"Etizolam": {"to": "Depas 1 mg",
                                                                                 "updated": "2026-11-26T19:00:00Z"}}}}))
assert "2026-11-10" in state["corrections"]["nights"] and "2026-11-11" in state["corrections"]["nights"]
assert state["corrections"]["names"]["Etizolam"]["to"] == "Depas 1 mg"
assert D(2026, 11, 10) not in ns["HealthStore"]().values["sleep_hours"]
print("sync ok")
