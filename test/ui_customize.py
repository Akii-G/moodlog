import os, json, tempfile, importlib.machinery, importlib.util
os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
cfg = tempfile.mkdtemp(); os.environ["MOODLOG_CONFIG_DIR"] = cfg
json.dump({"tour_done": True}, open(os.path.join(cfg, "config.json"), "w"))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
app = m.App("log"); res = []
def go():
    win = app.get_active_window()
    page = win.stack.get_page(win.stack.get_child_by_name("dreams"))
    res.append(page.get_visible())
    win.set_pref("dreams", False); res.append(page.get_visible())
    res.append(json.load(open(os.path.join(cfg, "config.json")))["prefs"]["dreams"])
    win.set_pref("dreams", True); res.append(page.get_visible())
    win.start_tour(); win.tour.go([k for k, *_ in m.TOUR_STEPS].index("customize")); res.append(win.tour.extra.get_visible())
    win.end_tour(); res.append(json.load(open(os.path.join(cfg, "config.json"))).get("customize_seen"))
    app.quit(); return False
GLib.timeout_add(1200, go); app.run([])
assert res == [True, False, False, True, True, True], res
print("customize OK")
