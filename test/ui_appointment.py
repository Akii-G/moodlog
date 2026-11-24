"""Before the appointment on the PC: the window, the Log card, the Events row and the report option."""
import os, sys, tempfile, datetime as dt, importlib.machinery, importlib.util
os.environ.update(MOODLOG_NO_WHATS_NEW="1", MOODLOG_DATA_DIR=tempfile.mkdtemp(),
                  MOODLOG_CONFIG_DIR=tempfile.mkdtemp(), MOODLOG_LANG="en")
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib, Gtk
s = m.Store(); s.add(dt.date.today() - dt.timedelta(days=2), "09:00", 2, "", feelings="tiredness"); s.save()
app = m.App("log"); err = []

def check():
    try:
        win = app.get_active_window()
        assert not win.appt_card.get_visible()
        w = m.AppointmentWindow(win); w.present()
        w.date_entry.set_text("not a date"); w.on_save_date()
        assert w.error.get_visible()
        w.date_entry.set_text((dt.date.today() + dt.timedelta(days=3)).isoformat())
        w.time_entry.set_text("16:30"); w.with_entry.set_text(" Dr   Rossi ")
        w.on_save_date()
        assert not w.error.get_visible()
        a = m.AppointmentStore()                           # saved to appointment.json
        assert a.appt["time"] == "16:30" and a.appt["with"] == "Dr Rossi"
        assert win.appt_card.get_visible() and "in 3 days" in win.appt_title.get_text()
        assert "1 day logged" in win.appt_recap.get_text()
        for t in ("Sleep is worse", "Ask about the evening pill"):
            w.point_entry.set_text(t); w.on_add()
        assert len(m.AppointmentStore().points) == 2 and "2 things" in win.appt_points.get_text()
        win.appointment.set_done(win.appointment.points[0], True); win.save_appointment()
        assert "1 thing" in win.appt_points.get_text()
        rep = m.ReportWindow(win)
        assert rep.points_check.get_visible() and not rep.points_check.get_active()
        rep.close()
        win.stack.set_visible_child_name("events"); win.refresh()
        assert "16:30" in win.appt_row_text.get_text()
        w.on_clear()
        assert not win.appt_card.get_visible() and win.appt_row_text.get_text() == "No appointment set"
        w.close()
    except Exception:
        import traceback; traceback.print_exc(); err.append(1)
    app.quit(); return False

GLib.timeout_add(1200, check)
app.run([])
print("FAIL" if err else "ui appointment ok")
sys.exit(1 if err else 0)
