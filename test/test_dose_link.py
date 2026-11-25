"""Dose on the same PC: its doses in Sottovoce (not taken / yet to take), the report's current
and previous medication, Dose's changes as events (once), and the copy sent to the phones."""
import datetime as dt, importlib.machinery, importlib.util, json, os, tempfile
dose_dir = tempfile.mkdtemp()
os.environ.update(MOODLOG_DATA_DIR=tempfile.mkdtemp(), MOODLOG_CONFIG_DIR=tempfile.mkdtemp(),
                  MOODLOG_LANG="en", DOSE_DATA_DIR=dose_dir)
here = os.path.dirname(os.path.abspath(__file__))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(here, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

today = dt.date.today()
d = lambda n: (today - dt.timedelta(days=n)).isoformat()
doc = {"medicines": [
    {"id": "a", "name": "Sertralina", "strength": "50 mg", "amount": 1.5, "unit": "pill",
     "schedule": {"kind": "daily", "times": ["00:00", "23:59"], "start": d(3), "end": ""}, "archived": False,
     "history": [{"date": d(3), "kind": "started", "detail": {"amount": 1, "unit": "pill", "strength": "50 mg",
                  "schedule": {"kind": "daily", "times": ["08:00"]}}},
                 {"date": d(1), "kind": "changed", "detail": {"amount": 1.5, "unit": "pill", "strength": "50 mg",
                  "schedule": {"kind": "daily", "times": ["00:00", "23:59"]}}}], "updated": "2026-01-01T00:00:00.000Z"},
    {"id": "b", "name": "Old pill", "amount": 1, "unit": "pill", "archived": True,
     "schedule": {"kind": "daily", "times": ["09:00"], "start": d(10), "end": ""},
     "history": [{"date": d(10), "kind": "started"}, {"date": d(5), "kind": "stopped"}], "updated": "2026-01-01T00:00:00.000Z"},
    {"id": "c", "name": "Ibuprofene", "amount": 1, "unit": "pill", "schedule": {"kind": "needed"}, "history": []}],
  "doses": [
    {"id": "a@%s@00:00" % d(3), "med": "a", "day": d(3), "time": "00:00", "status": "taken", "at": "00:10", "amount": 1},
    {"id": "a@%s@23:59" % d(3), "med": "a", "day": d(3), "time": "23:59", "status": "skipped", "at": "", "amount": 1},
    {"id": "b@%s@09:00" % d(6), "med": "b", "day": d(6), "time": "09:00", "status": "taken", "at": "09:00", "amount": 1},
    {"id": "x1", "med": "c", "day": d(0), "time": "", "status": "taken", "at": "12:00", "amount": 1}],
  "deleted": []}
json.dump(doc, open(os.path.join(dose_dir, "dose.json"), "w"))

meds = m.Medication()
assert meds.sources() == ["dose"]
today_doses = meds.on(today)
assert [(x[1], x[2]) for x in today_doses] == [("00:00", "pending"), ("12:00", "taken"), ("23:59", "pending")], today_doses
assert m.dose_status(today_doses[0]) == "Not taken" and m.dose_status(today_doses[2]) == "Yet to take"
assert m.dose_amount(today_doses[0]) == "1½ pills"
rows = {r[0]: r for r in meds.summary(today - dt.timedelta(days=30), today)}
# taken, skipped, not taken, yet to take
assert rows["Sertralina 50 mg"][1:5] == (1, 1, 5, 1), rows["Sertralina 50 mg"]
assert rows["Old pill"][1:5] == (1, 0, 0, 0) and meds.meds["Old pill"]["archived"]
assert meds.meds["Ibuprofene"]["as_needed"]

# events from Dose's history: once, and a deleted one stays deleted
ev = m.EventStore()
assert m.import_dose_events(ev) == 4
titles = sorted(e[2] for e in ev.items)
assert titles == ["New dose of Sertralina 50 mg", "Started Old pill", "Started Sertralina 50 mg", "Stopped Old pill"], titles
assert next(e for e in ev.items if e[2].startswith("New dose"))[3] == "1½ pills · every day at 00:00, 23:59"
ev.save()
ev.remove(ev.items[0]); ev.save()
assert m.import_dose_events(m.EventStore()) == 0

# the report: current medication, then previous medication
store = m.Store(); store.add(today, "09:00", 3, ""); store.save()
pdf = os.path.join(tempfile.mkdtemp(), "r.pdf")
m.export_report(pdf, store, m.EventStore(), m.HealthStore(), today - dt.timedelta(days=30), today, meds=meds)
import subprocess
text = subprocess.run(["pdftotext", "-layout", pdf, "-"], capture_output=True, text=True).stdout
assert text.rindex("Sertralina 50 mg") < text.index("Previous medication") < text.rindex("Old pill"), text
assert "Yet to take" in text and "Not taken" in text

# the phones get Dose's medication with the sync
state = m.sync_with_phone(json.dumps({"entries": [], "events": [], "deleted": []}))
assert {x["name"] for x in state["dose"]["medicines"]} == {"Sertralina 50 mg", "Old pill", "Ibuprofene"}
assert any(x["status"] == "pending" and x["time"] == "23:59" for x in state["dose"]["doses"])
print("dose link ok")
