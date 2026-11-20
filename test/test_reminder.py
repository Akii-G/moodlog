"""Reminder and kind word on the PC: the hard-days rule (the same cases as the phone apps),
the systemd timer, and --remind staying quiet when it should."""
import datetime as dt, json, os, tempfile, importlib.machinery, importlib.util
data = tempfile.mkdtemp(); cfgdir = tempfile.mkdtemp(); xdg = tempfile.mkdtemp()
os.environ.update(MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=cfgdir, XDG_CONFIG_HOME=xdg, MOODLOG_LANG="en")
here = os.path.dirname(os.path.abspath(__file__))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(here, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

T = dt.date(2026, 11, 20)

class Fake:
    def __init__(self, *days):
        self.avgs = {}
        for ago, mood in days:
            d = T - dt.timedelta(days=ago)
            self.avgs.setdefault(d, []).append(mood)
    def day_avg(self, d):
        v = self.avgs.get(d)
        return sum(v) / len(v) if v else 0

assert m.hard_stretch(Fake((0, 1), (1, 1), (3, 1)), T)
assert m.hard_stretch(Fake((1, 1), (2, 2), (2, 1), (4, 1)), T)
assert not m.hard_stretch(Fake((0, 1), (1, 1), (2, 3)), T), "only two hard days"
assert not m.hard_stretch(Fake((2, 1), (3, 1), (4, 1)), T), "not recent"
assert not m.hard_stretch(Fake((0, 2), (1, 2), (2, 2)), T), "bad, not awful"
assert not m.hard_stretch(Fake((0, 1), (1, 1), (6, 1)), T), "more than five days"

hard = Fake((0, 1), (1, 1), (2, 1))
assert m.show_kind_word(hard, {}, T)
assert not m.show_kind_word(hard, {"kind_word": False}, T)
assert not m.show_kind_word(hard, {"kind_hidden": "2026-11-17"}, T), "hidden 3 days ago"
assert m.show_kind_word(hard, {"kind_hidden": "2026-11-10"}, T), "hidden 10 days ago"

assert "Telefono Amico" not in str(m.helplines())          # English: the directory
assert any("findahelpline.com" in h[1] for h in m.helplines())

assert m.reminder_settings({}) == (False, "20:30")
assert m.reminder_settings({"reminder": {"on": True, "time": "25:99"}}) == (True, "20:30")
assert "OnCalendar=*-*-* 08:05:00" in m.timer_text("08:05")

# the timer file appears (systemctl may be missing here; the file is what matters)
m.apply_reminder(True, "21:15")
timer = os.path.join(xdg, "systemd", "user", "moodlog-reminder.timer")
assert "OnCalendar=*-*-* 21:15:00" in open(timer).read()
assert json.load(open(os.path.join(cfgdir, "config.json")))["reminder"] == {"on": True, "time": "21:15"}
m.apply_reminder(False, "21:15")
assert not os.path.exists(timer)

# --remind: off -> quiet; on but already logged today -> quiet (no D-Bus needed for either)
assert m.remind() == 0
m.apply_reminder(True, "21:15")
st = m.Store(); st.add(dt.date.today(), "09:00", 4, ""); st.save()
called = []
m.Gio.bus_get_sync = lambda *a: called.append(1)       # would show a notification
assert m.remind() == 0 and not called, "already logged today: no reminder"
m.apply_reminder(False, "21:15")
print("REMINDER OK")
