"""Privacy window: opens from the ☰ menu, shows the five points, and its link is the policy."""
import os, sys, json, subprocess, tempfile, importlib.machinery, importlib.util
os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
cfg = tempfile.mkdtemp(); os.environ["MOODLOG_CONFIG_DIR"] = cfg
json.dump({"tour_done": True}, open(os.path.join(cfg, "config.json"), "w"))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib, Gtk
tag = sys.argv[1] if len(sys.argv) > 1 else "en"; app = m.App("log"); err = []
def wins(): return [w for w in Gtk.Window.list_toplevels() if isinstance(w, m.PrivacyWindow) and w.get_visible()]
def s1():
    main = [w for w in Gtk.Window.list_toplevels() if isinstance(w, m.Window)][0]
    main.activate_action("win.privacy", None); return False
def s2():
    assert len(wins()) == 1, "privacy window not shown"
    assert len(m.PRIVACY_POINTS) == 5
    assert m.privacy_url().endswith("PRIVACY.it.md" if m.LANG_CODE == "it" else "PRIVACY.md")
    subprocess.run(["import", "-window", "root", "/tmp/privacy_%s.png" % tag]); return False
def s3():
    wins()[0].close(); app.quit(); return False
for t, f in ((1800, s1), (2600, s2), (3200, s3)):
    def run(f=f):
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    GLib.timeout_add(t, run)
app.run([]); print("ERRORS" if err else "PRIVACY OK")
