import os
import importlib.machinery, importlib.util, subprocess, sys
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
app = m.App("log"); out = sys.argv[1]
def shot():
    w = app.get_active_window()
    # tooltip/hit areas exist for every past day, grid days included
    assert len(w.calendar.hits) >= 10, len(w.calendar.hits)
    subprocess.run(["import", "-window", "root", out]); app.quit(); return False
GLib.timeout_add(2500, shot)
app.run([]); print("CAL OK")
