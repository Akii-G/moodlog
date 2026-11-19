import importlib.machinery, importlib.util, datetime as dt, os, sys, tempfile, csv
tmp = tempfile.mkdtemp(); os.environ["MOODLOG_DATA_DIR"] = tmp; os.environ["MOODLOG_GRIDGETS_DIR"] = "/nonexistent"
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
D = dt.date
# old-format file (v1.0-1.2: date,mood,note) still loads, as untimed entries
open(os.path.join(tmp, "moods.csv"), "w").write('date,mood,note\n2026-11-10,4,"hello, world"\n2026-11-11,,note only\n2026-11-12,9,\nbad,3,\n')
s = m.Store()
assert [e[:5] for e in s.entries(D(2026,11,10))] == [["", 4, "hello, world", 0, 0]] and [e[:5] for e in s.entries(D(2026,11,11))] == [["", 0, "note only", 0, 0]]
assert D(2026,11,12) not in s.days and s.moods() == [(D(2026,11,10), 4)]
# several entries a day, sorted by time whatever the order added
d = D(2026,11,13)
e_evening = s.add(d, "21:15", 2, "tired")
e_morning = s.add(d, "08:05", 5, "")
s.add(d, "13:40", 4, "lunch out")
s.add(d, "25:99", 3, "")                      # invalid time -> stored without a time
assert [e[0] for e in s.entries(d)] == ["", "08:05", "13:40", "21:15"]
assert abs(s.day_avg(d) - 3.5) < 1e-9 and s.day_level(d) == 4 and s.latest_mood(d) == 2
assert m.to_level(2.5) == 3 and m.to_level(3.5) == 4 and m.to_level(4.49) == 4 and m.to_level(1.0) == 1
assert s.timed(d, d) == [(d, "", 3), (d, "08:05", 5), (d, "13:40", 4), (d, "21:15", 2)]
tip = s.describe_day(d, "Fri 13 Nov"); assert "average 3.5 of 4 entries" in tip and "13:40 · Good · lunch out" in tip, tip
# edit time -> resort; remove by identity
e_morning[0] = "22:00"; s.resort(d); assert s.entries(d)[-1] is e_morning and s.latest_mood(d) == 5
s.remove(d, e_evening); assert all(e is not e_evening for e in s.entries(d)) and len(s.entries(d)) == 3
# save + reload round trip, new header
s.save(); rows = list(csv.reader(open(os.path.join(tmp, "moods.csv"))))
assert rows[0][:6] == ["date", "time", "mood", "energy", "anxiety", "note"] and ["2026-11-13", "13:40", "4", "", "", "lunch out"] in [r[:6] for r in rows]
s2 = m.Store(); assert s2.days == s.days
for e in list(s2.entries(d)): s2.remove(d, e)
assert d not in s2.days
# energy / anxiety: optional, averaged per day, saved and reloaded, entries with only a scale kept
d2 = D(2026,11,14)
s2.add(d2, "09:00", 4, "", 2, 0); s2.add(d2, "18:00", 0, "", 4, 5); s2.add(d2, "20:00", 3, "", 0, 9)
s2.save(); s3 = m.Store()
assert [e[:5] for e in s3.entries(d2)] == [["09:00", 4, "", 2, 0], ["18:00", 0, "", 4, 5], ["20:00", 3, "", 0, 0]], s3.entries(d2)
assert s3.day_scale(d2, 3) == 3.0 and s3.day_scale(d2, 4) == 5.0 and s3.day_avg(d2) == 3.5
assert s3.scale_series(3) == [(d2, 3.0)] and "energy 4, anxiety 5" in s3.describe_day(d2, "x")
print("store OK")
