import os, importlib.machinery, importlib.util, datetime as dt, tempfile, time, csv
tmp = tempfile.mkdtemp(); os.environ["MOODLOG_DATA_DIR"] = tmp; os.environ["MOODLOG_GRIDGETS_DIR"] = "/nonexistent"
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
D = dt.date
# legacy files (no ids) get the same stable ids on every load and every computer
open(os.path.join(tmp, "moods.csv"), "w").write("date,time,mood,energy,anxiety,note\n2026-11-01,09:00,4,,,hi\n2026-11-01,09:00,4,,,hi\n2026-11-02,,2,,,\n")
a, b = m.Store(), m.Store()
ids = [e[5] for d in sorted(a.days) for e in a.days[d]]
assert ids == [e[5] for d in sorted(b.days) for e in b.days[d]] and len(set(ids)) == 3, ids
a.save(); assert [e[5] for d in sorted(m.Store().days) for e in m.Store().days[d]] == ids
rows = list(csv.DictReader(open(os.path.join(tmp, "moods.csv")))); assert all(r["id"] for r in rows)
# newest change wins, both ways
pc, phone = m.Store(), m.Store()
_, e_pc = pc.find(ids[0]); _, e_ph = phone.find(ids[0])
e_ph[1] = 5; phone.touch(e_ph); time.sleep(0.01)
_, other = pc.find(ids[2]); other[2] = "edited on pc"; pc.touch(other)
assert pc.absorb(phone) == 1 and pc.find(ids[0])[1][1] == 5 and pc.find(ids[2])[1][2] == "edited on pc"
assert phone.absorb(pc) == 1 and phone.find(ids[2])[1][2] == "edited on pc"
assert pc.absorb(phone) == 0 and phone.absorb(pc) == 0                       # converged
# a deletion beats an older edit, an edit made after the deletion beats the deletion
day, e = phone.find(ids[1]); phone.remove(day, e)
assert pc.absorb(phone) == 1 and pc.find(ids[1])[1] is None and ids[1] in pc.deleted
pc.add(D(2026, 11, 5), "10:00", 3, "new on pc")
assert phone.absorb(pc) == 1 and any(e[2] == "new on pc" for e in phone.entries(D(2026, 11, 5)))
time.sleep(0.01); _, x = pc.find(ids[2]); old = x[6]
day, y = phone.find(ids[2]); phone.remove(day, y, stamp="2000-01-01T00:00:00.000Z")   # deleted "long ago"
assert pc.absorb(phone) == 0 and pc.find(ids[2])[1] is not None                       # newer edit survives
# deletions survive save/load
pc.save(); again = m.Store(); assert ids[1] in again.deleted and again.find(ids[1])[1] is None
# same content created separately on two devices is not duplicated
s1, s2 = m.Store(""), m.Store("")
s1.add(D(2026, 11, 9), "08:00", 4, "same"); s2.add(D(2026, 11, 9), "08:00", 4, "same")
assert s1.absorb(s2) == 0 and len(s1.entries(D(2026, 11, 9))) == 1
# merge-on-save: a change written by someone else in between is not lost
gui = m.Store(); time.sleep(0.02)
other = m.Store(); other.add(D(2026, 11, 10), "07:00", 1, "from the phone"); other.save()
gui.add(D(2026, 11, 10), "21:00", 5, "from the gui"); gui.save()
final = m.Store(); notes = [e[2] for e in final.entries(D(2026, 11, 10))]
assert notes == ["from the phone", "from the gui"], notes
# events: ids, edits, deletions, merge-on-save
ev = m.EventStore(); e1 = ev.add(D(2026, 11, 3), "medication", "Dose", ""); ev.save()
ev2 = m.EventStore(); ev2.update(ev2.items[0], D(2026, 11, 3), "medication", "Dose changed", "x"); ev2.save()
assert m.EventStore().items[0][2] == "Dose changed"
ev3 = m.EventStore(); ev3.remove(ev3.items[0]); ev3.save()
assert m.EventStore().items == [] and e1[4] in m.EventStore().deleted
open(os.path.join(tmp, "events.csv"), "w").write("date,kind,title,note\n2026-10-01,life,Moved,\n")
assert m.EventStore().items[0][4] == m.EventStore().items[0][4] and len(m.EventStore().items[0]) == 6
print("merge OK")
