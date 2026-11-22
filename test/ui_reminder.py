"""The reminder and kind word in Customize, and the kind word card on the Log page (hard days logged)."""
import os, sys, json, subprocess, tempfile, datetime as dt, importlib.machinery, importlib.util
os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
cfg = tempfile.mkdtemp(); os.environ["MOODLOG_CONFIG_DIR"] = cfg
json.dump({"tour_done": True}, open(os.path.join(cfg, "config.json"), "w"))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib, Gtk
st = m.Store()
for i in range(3):
    st.add(dt.date.today() - dt.timedelta(days=i), "08:00", 1, "")
st.save()
tag = sys.argv[1] if len(sys.argv) > 1 else "en"; app = m.App("log"); err = []
def main(): return [w for w in Gtk.Window.list_toplevels() if isinstance(w, m.Window)][0]
def s1():
    assert main().kind_card.get_visible(), "kind word not shown"
    subprocess.run(["import", "-window", "root", "/tmp/kind_pc_%s.png" % tag])
    main().activate_action("win.customize", None); return False
def s2():
    wins = [w for w in Gtk.Window.list_toplevels() if isinstance(w, m.CustomizeWindow) and w.get_visible()]
    assert len(wins) == 1
    # turning the kind word off in Customize hides the card at once
    switches = []
    def walk(w):
        if isinstance(w, Gtk.Switch): switches.append(w)
        c = w.get_first_child()
        while c is not None: walk(c); c = c.get_next_sibling()
    walk(wins[0])
    kind = switches[-1] if not m.GridgetsLink(m.Store()).available() else switches[-2]
    kind.set_active(False)
    assert not main().kind_card.get_visible() and m.load_config()["kind_word"] is False
    kind.set_active(True)
    subprocess.run(["import", "-window", "root", "/tmp/reminder_pc_%s.png" % tag]); wins[0].close()
    main().on_hide_kind_word()
    assert not main().kind_card.get_visible()
    app.quit(); return False
for t, f in ((2000, s1), (3000, s2)):
    def run(f=f):
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    GLib.timeout_add(t, run)
app.run([]); print("ERRORS" if err else "REMINDER UI OK")
