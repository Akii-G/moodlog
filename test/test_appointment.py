"""Before the appointment: the store, its sync merge, the recap; and "Your week, gently"."""
import datetime as dt, json, os, tempfile, importlib.machinery, importlib.util
data = tempfile.mkdtemp(); cfgdir = tempfile.mkdtemp()
os.environ.update(MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=cfgdir, MOODLOG_LANG="en")
here = os.path.dirname(os.path.abspath(__file__))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(here, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

today = dt.date(2026, 11, 24)
a = m.AppointmentStore()
assert a.appt["date"] is None and not a.card_shown(today)
a.set_appointment(dt.date(2026, 11, 5), "10:00", "Dr Rossi", today=dt.date(2026, 11, 1))
assert a.appt["previous"] is None
# the old one has happened: setting the next makes it "previous", where the recap starts
a.set_appointment(dt.date(2026, 11, 27), "16:30", "Dr Rossi", today=today)
assert a.appt["previous"] == dt.date(2026, 11, 5) and a.recap_start(today) == dt.date(2026, 11, 5)
assert a.card_shown(today) and not a.card_shown(dt.date(2026, 11, 19)) and a.card_shown(dt.date(2026, 11, 28))
assert not a.card_shown(dt.date(2026, 11, 29))
assert "in 3 days" in m.appointment_when(a.appt, today) and "16:30" in m.appointment_when(a.appt, today)
p1 = a.add_point("  The  new dose makes me  sleepy ")
p2 = a.add_point("Ask about therapy")
assert p1["text"] == "The new dose makes me sleepy"
a.set_done(p2, True)
assert a.open_points() == [p1]
a.save()

# a round trip through the file and through the sync JSON keeps everything
b = m.AppointmentStore()
assert b.appt == a.appt and b.points == a.points
doc = json.loads(json.dumps(m.stores_to_json(m.Store(""), m.EventStore(""), appointment=a)))
assert doc["appointment"]["date"] == "2026-11-27" and len(doc["points"]) == 2

# the phone deletes a point and edits the appointment later: those win
phone = m.AppointmentStore("")
phone.absorb_json(doc)
phone.remove_point(phone.points[0], stamp="2999-01-01T00:00:00.000Z")
phone.appt = dict(phone.appt, time="17:00", updated="2999-01-01T00:00:00.000Z")
changed = a.absorb_json(json.loads(json.dumps(m.stores_to_json(m.Store(""), m.EventStore(""), appointment=phone))))
assert changed == 2 and [p["text"] for p in a.points] == ["Ask about therapy"] and a.appt["time"] == "17:00"
# an older copy of the deleted point does not come back
assert a.absorb_json({"points": [dict(p1)]}) == 0 and len(a.points) == 1
# point tombstones are not mistaken for deleted moods
st, _ev = m.stores_from_json({"deleted": [{"id": "x", "type": "point", "deleted": "2026-11-01T00:00:00.000Z"}]})
assert not st.deleted
# junk is ignored
assert m.clean_point({"id": "a", "text": " ", "updated": "2026-11-01T00:00:00.000Z"}) is None
assert m.clean_appointment({"date": "nope", "updated": "2026-11-01T00:00:00.000Z"})["date"] is None

# recap since the last appointment
s = m.Store("")
s.add(dt.date(2026, 11, 10), "09:00", 2, "", feelings="tiredness,worry")
s.add(dt.date(2026, 11, 11), "09:00", 2, "", feelings="tiredness")
s.add(dt.date(2026, 11, 12), "09:00", 4, "")
ev = m.EventStore(""); ev.add(dt.date(2026, 11, 11), "medication", "New dose", "")
r = m.appointment_recap(s, ev, dt.date(2026, 11, 5), today)
assert r == "Since 5 November: 3 days logged · mostly bad · often Tiredness · 1 event.", r
assert "no moods" in m.appointment_recap(m.Store(""), ev, dt.date(2026, 11, 5), today)

# the weekly look back: Sunday from 18:00 and all Monday, about Monday-Sunday
w = m.Store("")
for d, mood, f in ((16, 3, "tiredness"), (17, 4, "tiredness"), (19, 5, "joy"), (21, 3, "tiredness")):
    w.add(dt.date(2026, 11, d), "09:00", mood, "", feelings=f)
assert m.week_look_back(w, dt.datetime(2026, 11, 22, 17, 0)) is None          # Sunday afternoon
monday, text_ = m.week_look_back(w, dt.datetime(2026, 11, 22, 19, 0))
assert monday == dt.date(2026, 11, 16)
assert text_ == "4 days logged, mostly okay. Tiredness came up 3 times. Thursday was your best day.", text_
assert m.week_look_back(w, dt.datetime(2026, 11, 23, 9, 0))[0] == dt.date(2026, 11, 16)
assert m.week_look_back(w, dt.datetime(2026, 11, 24, 9, 0)) is None            # Tuesday
assert m.week_look_back(m.Store(""), dt.datetime(2026, 11, 23, 9, 0)) is None  # nothing logged
print("appointment ok")
