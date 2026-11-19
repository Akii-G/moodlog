import os, sys, json, subprocess, tempfile, importlib.machinery, importlib.util
# Simulate an update from 2.0.1 (style tahoe if argv[2] == "tahoe")
os.environ.pop("MOODLOG_NO_WHATS_NEW", None); cfg = tempfile.mkdtemp(); os.environ["MOODLOG_CONFIG_DIR"] = cfg
style = sys.argv[2] if len(sys.argv) > 2 else "default"
json.dump({"style": style, "last_seen_version": "2.0.1"}, open(os.path.join(cfg, "config.json"), "w"))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib, Gtk
tag = sys.argv[1]; app = m.App("log"); err = []
def wins(): return [w for w in Gtk.Window.list_toplevels() if isinstance(w, m.ChangelogWindow) and w.get_visible()]
def s1():
    assert len(wins()) == 1, "changelog not shown after update"
    assert json.load(open(os.path.join(cfg, "config.json")))["last_seen_version"] == m.VERSION
    assert m.CHANGELOG[0][0] == m.VERSION and "### 1.0.0" in m.changelog_markdown() and "## Moodlog for iPhone" in m.changelog_markdown()
    return False
def s2():
    subprocess.run(["import", "-window", "root", "/tmp/changelog_%s.png" % tag])
    wins()[0].close(); return False
def s3():
    assert not wins()
    main = [w for w in Gtk.Window.list_toplevels() if isinstance(w, m.Window)][0]
    main.activate_action("win.changelog", None); return False
def s4():
    assert len(wins()) == 1, "menu item did not open it"; wins()[0].close()
    # second start: same version, must not show again
    main = [w for w in Gtk.Window.list_toplevels() if isinstance(w, m.Window)][0]
    main.show_changelog_after_update(); return False
def s5():
    assert not wins(), "shown twice"
    # brand-new install: no key, no data -> silent
    json.dump({}, open(os.path.join(cfg, "config.json"), "w"))
    main = [w for w in Gtk.Window.list_toplevels() if isinstance(w, m.Window)][0]
    main.store.days = {}; main.events.items = []
    main.show_changelog_after_update(); return False
def s6():
    assert not wins(), "shown on fresh install"
    assert json.load(open(os.path.join(cfg, "config.json")))["last_seen_version"] == m.VERSION
    app.quit(); return False
for t, f in ((1800, s1), (2800, s2), (3400, s3), (4200, s4), (4800, s5), (5400, s6)):
    def run(f=f):
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    GLib.timeout_add(t, run)
app.run([]); print("ERRORS" if err else "CHANGELOG OK")
