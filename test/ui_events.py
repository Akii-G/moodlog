import os; os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
import os
import importlib.machinery, importlib.util, subprocess, datetime as dt, sys
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib, Gtk
app = m.App("log"); err = []; tag = sys.argv[1]
today = dt.date.today()
def kids(box):
    out, c = [], box.get_first_child()
    while c: out.append(c); c = c.get_next_sibling()
    return out
def guard(f):
    def run():
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    return run
def s1():
    w = app.get_active_window()
    assert len(w.events.items) == 4
    # select the day of the dose change: its event shows under the day title
    day = today - dt.timedelta(days=9)
    w.select_day(day); assert len(kids(w.day_events)) == 1 and w.day_events.get_visible()
    # add an event from the Log tab
    win = m.EventWindow(w, day=w.selected); win.present()
    win.title_entry.set_text("   "); win.on_save(); assert win.error.get_visible() and len(w.events.items) == 4
    win.date_entry.set_text("17/11/2026"); win.title_entry.set_text("Missed doses"); win.on_save()
    assert "year-month-day" in win.error.get_text() and len(w.events.items) == 4
    win.date_entry.set_text(day.isoformat()); win.kind_drop.set_selected(1); win.on_save()
    assert len(w.events.items) == 5 and len(kids(w.day_events)) == 2
    added = [e for e in w.events.items if e[2] == "Missed doses"][0]; assert added[1] == "health"
    # edit it, then delete it
    ed = m.EventWindow(w, event=added); ed.title_entry.set_text("Missed two doses"); ed.on_save()
    assert added[2] == "Missed two doses" and m.EventStore().items[-3][2] in ("Missed two doses", "Dose increased")
    de = m.EventWindow(w, event=added); de.on_delete()
    assert len(w.events.items) == 4 and len(m.EventStore().items) == 4 and len(kids(w.day_events)) == 1
    # calendar hit text includes the event
    w.select_day(today); w.calendar.queue_draw()
    return False
def s2():
    w = app.get_active_window()
    tips = [h[4] for h in w.calendar.hits if "Dose increased" in h[4]]
    assert tips and "Event — Medication change" in tips[0], tips
    assert any("Check-up" in h[4] for h in w.calendar.hits), "planned future event has a tooltip"
    subprocess.run(["import", "-window", "root", "/tmp/ev_log_%s.png" % tag])
    w.set_default_size(980, 1000); w.stack.set_visible_child_name("charts"); return False
def s3():
    w = app.get_active_window()
    assert any("Dose increased" in h[4] for h in w.trend.hits), "event line on the trend chart"
    subprocess.run(["import", "-window", "root", "/tmp/ev_charts_%s.png" % tag])
    w.range_drop.set_selected(2); return False
def s4():
    w = app.get_active_window()
    assert any("Started new medication" in h[4] for h in w.trend.hits), "event in all-time view"
    w.stack.set_visible_child_name("events"); return False
def s5():
    w = app.get_active_window()
    rows = kids(w.events_list); assert len(rows) == 4 and rows[0].event[2] == "Check-up"
    subprocess.run(["import", "-window", "root", "/tmp/ev_list_%s.png" % tag]); app.quit(); return False
for t, f in ((1500, s1), (3000, s2), (4500, s3), (6000, s4), (7500, s5)):
    GLib.timeout_add(t, guard(f))
app.run([]); print("ERRORS" if err else "EVENTS UI OK")
