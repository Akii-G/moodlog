import importlib.machinery, importlib.util, datetime as dt, json, os, sys, tempfile, time
tmp = tempfile.mkdtemp()
os.environ["MOODLOG_DATA_DIR"] = os.path.join(tmp, "moodlog")
os.environ["MOODLOG_GRIDGETS_DIR"] = os.path.join(tmp, "gridgets")
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
today = dt.date.today(); key = today.strftime("%Y-%m")
def d(n): return today - dt.timedelta(days=n)
# keep every test date inside the current month
assert today.day >= 6, "test needs day-of-month >= 6"
gpath = os.path.join(tmp, "gridgets", "mood", today.strftime("%Y"), today.strftime("%m") + ".json")
def widget_writes(levels, when=None):
    """Simulate Gridgets rewriting the month file (indent 2, like saveJsonToFile)."""
    os.makedirs(os.path.dirname(gpath), exist_ok=True)
    json.dump({k.isoformat(): v for k, v in levels.items()}, open(gpath, "w"), indent=2)
    ts = time.mktime(dt.datetime.combine(when or today, dt.time(12, 0)).timetuple()) + widget_writes.n
    widget_writes.n += 1
    os.utime(gpath, (ts, ts))
widget_writes.n = 0
def widget_file(): return {dt.date.fromisoformat(k): v for k, v in json.load(open(gpath)).items()}
def moods(store): return {day: store.latest_mood(day) for day, _l in store.moods()}
def setm(store, day, mood, note):
    """Replace a day's entries with one untimed entry (or clear the day)."""
    for e in list(store.entries(day)): store.remove(day, e)
    if mood or note.strip(): store.add(day, "", mood, note)
def getm(store, day):
    notes = [e[2] for e in store.entries(day) if e[2]]
    return (store.latest_mood(day), notes[0] if notes else "")

store = m.Store()
# 0. Gridgets not installed -> link inactive, nothing created
link = m.GridgetsLink(store)
assert not link.available() and link.reconcile_all() is False and not os.path.exists(os.path.join(tmp, "gridgets"))

# 1. first link: widget history is imported, Moodlog-only days are written out, notes kept
setm(store, d(1), 5, "my note")            # logged in Moodlog only
setm(store, d(3), 2, "keep me")            # logged in both, different value, not the widget's last day
widget_writes({d(5): 4, d(4): 1, d(3): 3, d(2): 4}, when=d(2))
assert link.available() and link.reconcile_all() is True
assert moods(store) == {d(5): 4, d(4): 1, d(3): 2, d(2): 4, d(1): 5}, moods(store)
assert getm(store, d(1)) == (5, "my note") and getm(store, d(3)) == (2, "keep me")
assert widget_file() == moods(store)
assert link.reconcile_all() is False and not link.changed_on_disk()      # stable

# 2. mood picked in the widget today -> arrives in Moodlog
stale = dict(widget_file()); stale[today] = 4
widget_writes(stale)
assert link.changed_on_disk() and link.reconcile_all() is True and getm(store, today)[0] == 4

# 3. changed in Moodlog (today and a past day) -> written to the widget file
setm(store, today, 5, "better now"); setm(store, d(4), 3, "")
assert link.reconcile_all() is False
assert widget_file()[today] == 5 and widget_file()[d(4)] == 3

# 4. widget saves from its stale copy (old today=4, old d(4)=1) with a new click today=2:
#    today's click wins, the stale past day does not come back, the note survives
stale[today] = 2
widget_writes(stale)
assert link.reconcile_all() is True
assert getm(store, today) == (2, "better now") and getm(store, d(4))[0] == 3
assert widget_file()[d(4)] == 3 and widget_file()[today] == 2

# 5. day cleared in Moodlog -> removed from the widget file, and a stale widget save does not revive it
setm(store, d(5), 0, "")
assert link.reconcile_all() is False and d(5) not in widget_file()
stale[today] = 3
widget_writes(stale)                      # still contains d(5)=4
assert link.reconcile_all() is True and getm(store, today)[0] == 3
assert d(5) not in moods(store) and d(5) not in widget_file()

# 6. state survives a restart; nothing to do on reopen
store.save()
link2 = m.GridgetsLink(m.Store())
assert link2.reconcile_all() is False and d(5) not in widget_file()

# 7. widget used while Moodlog was closed, on an earlier day than today (file mtime = that day)
s3 = m.Store(); setm(s3, d(1), 0, "note only")      # no mood for yesterday, note kept
l3 = m.GridgetsLink(s3); l3.reconcile_all()
assert d(1) not in widget_file() and getm(s3, today)[0] == 3 and getm(s3, d(4))[0] == 3, (widget_file(), getm(s3, today))
w = dict(widget_file()); w[d(1)] = 4
widget_writes(w, when=d(1))
assert l3.reconcile_all() is True and getm(s3, d(1)) == (4, "note only")

# 7b. two picks in the widget on the same day -> two timed entries, the widget keeps the latest
n_before = len(s3.entries(today))
w = dict(widget_file()); w[today] = 1; widget_writes(w); assert l3.reconcile_all() is True
w[today] = 5; widget_writes(w); assert l3.reconcile_all() is True
added = s3.entries(today)[n_before:]
assert [e[1] for e in added] == [1, 5] and all(m.TIME_RE.match(e[0]) for e in added), added
assert s3.latest_mood(today) == 5 and widget_file()[today] == 5
s3.save(); assert [e[1] for e in m.Store().entries(today)][-2:] == [1, 5]      # survives save + reload

# 8. broken widget file is left untouched; junk values and future dates are ignored
open(gpath, "w").write("{not json"); before = open(gpath).read()
assert l3.reconcile_all() is False and open(gpath).read() == before
json.dump({today.isoformat(): 9, "hello": 3, (today + dt.timedelta(days=1)).isoformat() if (today + dt.timedelta(days=1)).month == today.month else "x": 5, d(2).isoformat(): True}, open(gpath, "w"))
truth = moods(s3)
assert l3.reconcile_all() is False and moods(s3) == truth and widget_file() == truth   # junk ignored, file repaired

# 9. switched off -> no reading, no writing
l3.enabled = False; open(gpath, "w").write("{}"); assert l3.reconcile_all() is False and open(gpath).read() == "{}"
print("gridgets link OK")
