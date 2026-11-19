import os; os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
"""Screenshot the real app: shot_app.py OUT.png PAGE STYLE WIDTH HEIGHT [range]"""
import os, sys, json, tempfile, subprocess, importlib.machinery, importlib.util, datetime as dt
out, page, style, W, H = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
rng = sys.argv[6] if len(sys.argv) > 6 else "month"
cfg = tempfile.mkdtemp(); os.environ["MOODLOG_CONFIG_DIR"] = cfg
json.dump({"style": style}, open(os.path.join(cfg, "config.json"), "w"))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
app = m.App(page)
def setup():
    w = app.get_active_window()
    w.set_default_size(W, H)
    if page == "log":
        day = dt.date.today() - dt.timedelta(days=1)
        while len(w.store.entries(day)) < 2: day -= dt.timedelta(days=1)
        w.select_day(day)
    if page == "charts" and rng != "month":
        w.range_drop.set_selected({"year": 1, "all": 2}[rng])
    return False
def shoot():
    w = app.get_active_window()
    subprocess.run(["xdotool", "mousemove", "1095", "1495"])  # pointer off the window: no hover
    subprocess.run(["import", "-window", "root", "-crop", "%dx%d+0+0" % (w.get_width(), w.get_height()), "+repage", out])
    app.quit(); return False
GLib.timeout_add(800, setup)
GLib.timeout_add(1500, lambda: (subprocess.run(["xdotool", "mousemove", "1095", "1495"]), False)[1])
GLib.timeout_add(3600, shoot)
app.run([])
