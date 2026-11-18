import importlib.machinery, importlib.util, datetime as dt, os, tempfile, csv
tmp = tempfile.mkdtemp(); os.environ["MOODLOG_DATA_DIR"] = tmp; os.environ["MOODLOG_GRIDGETS_DIR"] = "/nonexistent"
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
today = dt.date.today(); D = lambda n: today - dt.timedelta(days=n)
ev = m.EventStore(); assert ev.items == []
e1 = ev.add(D(20), "medication", "Started new dose", "from 50 to 75")
e2 = ev.add(D(40), "life", "Moved house", "")
ev.add(today + dt.timedelta(days=3), "medication", "Planned change", "")
assert [e[0] for e in ev.items] == [D(40), D(20), today + dt.timedelta(days=3)]
ev.save(); ev2 = m.EventStore(); assert ev2.items == ev.items
rows = list(csv.reader(open(os.path.join(tmp, "events.csv")))); assert rows[0] == ["date", "kind", "title", "note"]
open(os.path.join(tmp, "events.csv"), "a").write("bad,medication,x,\n2026-01-01,weird,Kept as other,\n2026-01-02,health,,\n")
ev3 = m.EventStore(); assert ["other"] == [e[1] for e in ev3.items if e[2] == "Kept as other"] and len(ev3.items) == 4
assert ev.on(D(20)) == [e1] and ev.between(D(30), today) == [e1]
ev.remove(e2); assert e2 not in ev.items and len(ev.items) == 2
# before/after averages
st = m.Store()
for n in range(34, 20, -1): st.add(D(n), "", 2, "")        # 14 days before: all "Bad"
for n in range(20, 6, -1): st.add(D(n), "09:00", 4, ""); st.add(D(n), "20:00", 5, "")   # after: 4.5
(b, nb), (a, na), complete = m.mood_around(st, D(20))
assert nb == 14 and abs(b - 2) < 1e-9 and na == 14 and abs(a - 4.5) < 1e-9 and complete
txt = m.event_summary(st, e1); assert "2.0 (14 days logged)" in txt and "4.5 (14 days logged)" in txt and "so far" not in txt, txt
recent = [D(2), "health", "x", ""]; assert "so far" in m.event_summary(st, recent) and "not enough" in m.event_summary(st, recent)
assert m.event_summary(st, [today + dt.timedelta(days=1), "other", "x", ""]).startswith("Planned")
print("events OK")
