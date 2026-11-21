"""Private notes and dreams: kept in the files and in sync, never in the doctor report's text
(private dreams are still counted)."""
import datetime as dt, json, os, subprocess, tempfile, importlib.machinery, importlib.util
data = tempfile.mkdtemp(); os.environ.update(MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=tempfile.mkdtemp(), MOODLOG_LANG="en")
here = os.path.dirname(os.path.abspath(__file__))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(here, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
d = dt.date(2026, 11, 20)
st = m.Store(); st.add(d, "09:00", 4, "PUBLICNOTE"); st.add(d, "21:00", 2, "SECRETNOTE", private=True); st.save()
ds = m.DreamStore(); ds.add(d, "neutral", "", "PUBLICDREAM"); ds.add(d, "disturbing", "nightmare", "SECRETDREAM", private=True); ds.save()
# kept in the files
st2, ds2 = m.Store(), m.DreamStore()
assert [m.is_private(e, 7) for e in st2.entries(d)] == [False, True]
assert sorted(m.is_private(x, 6) for x in ds2.items) == [False, True]
# and in what goes to the phones (only when true), and back
doc = m.stores_to_json(st2, m.EventStore(), ds2)
assert [e.get("private") for e in doc["entries"]] == [None, True]
assert sorted(str(x.get("private")) for x in doc["dreams"]) == ["None", "True"]
back, _ev = m.stores_from_json(json.loads(json.dumps(doc)))
assert [m.is_private(e, 7) for e in back.entries(d)] == [False, True]
assert sorted(m.is_private(x, 6) for x in m.dreams_from_json(doc).items) == [False, True]
# a file from before (no private column) still loads, as not private
open(os.path.join(data, "old.csv"), "w").write("date,time,mood,energy,anxiety,note,id,updated,deleted\n2026-11-01,09:00,4,,,hi,a1,2026-11-01T00:00:00Z,\n")
assert not m.is_private(m.Store(os.path.join(data, "old.csv")).entries(dt.date(2026, 11, 1))[0], 7)
# the report: with notes and dream text asked for, the private ones stay out
out = os.path.join(data, "r.pdf")
m.export_report(out, st2, m.EventStore(), m.HealthStore(), dt.date(2026, 11, 1), d,
                include_notes=True, dreams=ds2, dream_mode="text")
text = subprocess.run(["pdftotext", out, "-"], capture_output=True, text=True).stdout
assert "PUBLICNOTE" in text and "PUBLICDREAM" in text, text
assert "SECRETNOTE" not in text and "SECRETDREAM" not in text, text
assert "2 nights with a dream entry" in text or "1 night with a dream entry" in text, text
assert "1 with a nightmare" in text, "private dreams are still counted"
print("private OK")
