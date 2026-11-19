import os; os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
import os, sys, subprocess, tempfile, importlib.machinery, importlib.util
cfg = tempfile.mkdtemp(); os.environ["MOODLOG_CONFIG_DIR"] = cfg
import json; json.dump({"style": "tahoe"}, open(os.path.join(cfg, "config.json"), "w"))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
tag = sys.argv[1]; app = m.App("log"); err = []
def s1():
    w = app.get_active_window()
    assert m.STYLE["name"] == "tahoe" and m.theme()["radius"] == 14
    assert w.lookup_action("style").get_state().get_string() == "tahoe"
    w.set_default_size(980, 700)
    return False
def s2():
    subprocess.run(["import", "-window", "root", "/tmp/tahoe_log_%s.png" % tag])
    w = app.get_active_window(); w.set_default_size(980, 1000); w.stack.set_visible_child_name("charts"); return False
def s3():
    subprocess.run(["import", "-window", "root", "/tmp/tahoe_charts_%s.png" % tag])
    w = app.get_active_window(); w.stack.set_visible_child_name("events"); return False
def s4():
    subprocess.run(["import", "-window", "root", "/tmp/tahoe_events_%s.png" % tag])
    w = app.get_active_window()
    w.activate_action("win.style", GLib.Variant.new_string("default"))       # switch back live
    assert m.STYLE["name"] == "default" and m._style_provider["css"] is None and m.theme()["radius"] == 10
    assert json.load(open(os.path.join(cfg, "config.json")))["style"] == "default"
    w.activate_action("win.style", GLib.Variant.new_string("tahoe")); assert m._style_provider["css"] is not None
    app.quit(); return False
for t, f in ((1200, s1), (2600, s2), (4200, s3), (5800, s4)):
    def run(f=f):
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    GLib.timeout_add(t, run)
app.run([]); print("ERRORS" if err else "STYLE OK")
