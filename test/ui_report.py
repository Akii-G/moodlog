import os; os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
import os, datetime as dt, subprocess, importlib.machinery, importlib.util
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
app = m.App("log"); err = []; today = dt.date.today()
def s1():
    try:
        w = app.get_active_window()
        assert w.lookup_action("report")
        r = m.ReportWindow(w); r.present(); app.r = r
        assert r.end_entry.get_text() == today.isoformat() and r.start_entry.get_text() == (today - dt.timedelta(days=90)).isoformat()
        r.preset(30); assert r.start_entry.get_text() == (today - dt.timedelta(days=29)).isoformat()
        r.preset("year"); assert r.start_entry.get_text() == "%d-01-01" % today.year
        r.start_entry.set_text("2026-13-01"); assert r.dates() is None and r.error.get_visible()
        r.start_entry.set_text("2026-12-01"); r.end_entry.set_text("2026-11-01"); assert r.dates() is None and "before" in r.error.get_text()
        r.preset(91); assert r.health_check.get_visible() and not r.notes_check.get_active()
        GLib.timeout_add(1500, s2)
    except Exception:
        import traceback; traceback.print_exc(); err.append(1); app.quit()
    return False
def s2():
    try:
        subprocess.run(["import", "-window", "root", "/tmp/report_window.png"])
        start, end = app.r.dates()
        app.r.write("/tmp/rep/from_ui", start, end)
        assert os.path.getsize("/tmp/rep/from_ui.pdf") > 5000
    except Exception:
        import traceback; traceback.print_exc(); err.append(1)
    app.quit(); return False
GLib.timeout_add(1500, s1)
app.run([]); print("ERRORS" if err else "REPORT UI OK")
