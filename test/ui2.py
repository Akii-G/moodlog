import os; os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
import os
import importlib.machinery, importlib.util, subprocess, sys
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
app = m.App("charts"); err = []
def guard(f):
    def run():
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    return run
def s1():
    w = app.get_active_window(); w.set_default_size(980, 1180); w.shift(-1, charts=True); return False
def s2():
    subprocess.run(["import", "-window", "root", "/tmp/h_month.png"])
    w = app.get_active_window(); w.metric_drop.set_selected(2); assert w.health_metric == "resting_hr"; w.range_drop.set_selected(2); return False
def s3():
    subprocess.run(["import", "-window", "root", "/tmp/h_all.png"])
    w = app.get_active_window(); w.metric_drop.set_selected(1); w.range_drop.set_selected(1)
    sw = m.SyncWindow(w); sw.present(); sw.on_switch(sw.switch, True); app.sw = sw; return False
def s4():
    subprocess.run(["import", "-window", "root", "/tmp/h_sync.png"]); app.sw.close(); return False
def s5():
    subprocess.run(["import", "-window", "root", "/tmp/h_year.png"]); app.quit(); return False
for t, f in ((1500, s1), (3000, s2), (4500, s3), (6500, s4), (8000, s5)):
    GLib.timeout_add(t, guard(f))
app.run([]); print("ERRORS" if err else "UI OK")
