"""Medication from Apple Health on the PC: the lines Moodlog for iPhone sends, keeping only
what Health still has for the period sent, the day view text and the doctor report."""
import datetime as dt, os, tempfile, importlib.machinery, importlib.util
data = tempfile.mkdtemp(); cfgdir = tempfile.mkdtemp()
os.environ.update(MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=cfgdir, MOODLOG_LANG="en")
here = os.path.dirname(os.path.abspath(__file__))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(here, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

def local(day, hh, mm):
    """An ISO time with the PC's own offset, as the phone sends it."""
    when = dt.datetime.combine(dt.date.fromisoformat(day), dt.time(hh, mm)).astimezone()
    return when.isoformat()

first = "\n".join([
    "steps;2026-11-18T00:00:00+01:00;8000",
    "med_range;2026-11-01;2026-11-20",
    "med;active;scheduled;Sertraline",
    "med;archived;as_needed;Ibuprofen; with food",
    "dose;%s;taken;50;mg;Sertraline" % local("2026-11-18", 8, 10),
    "dose;%s;skipped;;;Sertraline" % local("2026-11-19", 8, 0),
    "dose;%s;taken;1;count;Ibuprofen; with food" % local("2026-11-19", 13, 30),
    "dose;%s;snoozed;50;mg;Sertraline" % local("2026-11-20", 8, 0),   # not counted
])
ok, message = m.store_health_lines(first.encode())
assert ok, message
assert "steps for 1 day" in message and "medication for 2 days" in message, message

meds = m.MedicationStore()
assert meds.meds["Ibuprofen; with food"] == {"archived": True, "as_needed": True}
assert [d[2] for d in meds.doses] == ["taken", "skipped", "taken"]
assert m.dose_text(meds.on(dt.date(2026, 11, 18))[0]) == "08:10 Sertraline 50 mg"
assert m.dose_text(meds.on(dt.date(2026, 11, 19))[1]) == "13:30 Ibuprofen; with food 1"
assert m.MedicationStore().summary(dt.date(2026, 11, 1), dt.date(2026, 11, 30)) == [
    ("Ibuprofen; with food", 1, 0, 0, 0, dt.date(2026, 11, 19), dt.date(2026, 11, 19)),
    ("Sertraline", 1, 1, 0, 0, dt.date(2026, 11, 18), dt.date(2026, 11, 19))]

# A later send covers 19-20 November only: the dose deleted in Health goes, the 18th stays.
later = "\n".join(["med_range;2026-11-19;2026-11-20",
                   "med;active;scheduled;Sertraline",
                   "dose;%s;taken;50;mg;Sertraline" % local("2026-11-20", 8, 5)])
ok, message = m.store_health_lines(later.encode())
assert ok and message == "OK. Stored medication for 1 day.", message
meds = m.MedicationStore()
assert [(d[0].day, d[2]) for d in meds.doses] == [(18, "taken"), (20, "taken")]
assert list(meds.meds) == ["Sertraline"]

# Health lines without any medication don't touch the medication file.
ok, _ = m.store_health_lines(b"steps;2026-11-19T00:00:00+01:00;5000")
assert len(m.MedicationStore().doses) == 2

# The report: with and without the medication section.
store = m.Store(); events = m.EventStore(); health = m.HealthStore()
store.add(dt.date(2026, 11, 18), "09:00", 4, "")
out = os.path.join(data, "r.pdf")
assert m.export_report(out, store, events, health, dt.date(2026, 11, 1), dt.date(2026, 11, 20),
                       meds=meds) >= 1
with_meds = os.path.getsize(out)
m.export_report(out, store, events, health, dt.date(2026, 11, 1), dt.date(2026, 11, 20))
assert with_meds > os.path.getsize(out), "the medication section adds to the PDF"
# A dose not answered: yet to take on its day before its time, not taken after.
ok, _ = m.store_health_lines(("med_range;2026-11-21;2026-11-21\ndose;%s;pending;1;count;Litio" % local("2026-11-21", 21, 30)).encode())
meds = m.MedicationStore()
assert meds.on(dt.date(2026, 11, 21))[0][2] == "pending"
assert m.dose_status(meds.on(dt.date(2026, 11, 21))[0], today=dt.date(2026, 11, 21)) == "Yet to take"
assert m.dose_status(meds.on(dt.date(2026, 11, 21))[0], today=dt.date(2026, 11, 22)) == "Not taken"
assert m.dose_amount(meds.on(dt.date(2026, 11, 21))[0]) == "× 1"
assert [r for r in meds.summary(dt.date(2026, 11, 1), dt.date(2026, 11, 30)) if r[0] == "Litio"][0][3:5] == (1, 0)
assert "medications.json" in m.CONTENT_FILES
print("medications OK")
