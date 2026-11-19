import os; os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
import importlib.machinery, importlib.util, subprocess, os, json, datetime as dt
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
app = m.App("log"); err = []
today = dt.date.today()
gpath = os.path.join(os.environ["MOODLOG_GRIDGETS_DIR"], "mood", today.strftime("%Y"), today.strftime("%m") + ".json")
def guard(f):
    def run():
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    return run
def s1():
    w = app.get_active_window()
    assert w.link.active() and os.path.exists(gpath)
    data = json.load(open(gpath)); assert data, "moodlog moods exported to widget file"
    w.note_view.get_buffer().set_text("typing a note")          # user is mid-note on today
    return False
def s2():
    data = json.load(open(gpath)); data[today.isoformat()] = 1   # widget click: Sad
    json.dump(data, open(gpath, "w"), indent=2); return False
def s3():
    w = app.get_active_window()
    ents = w.store.entries(today)
    assert [e[1] for e in ents][-1] == 1 and w.store.latest_mood(today) == 1, ents      # widget pick = new timed entry
    assert m.TIME_RE.match(ents[-1][0]) and w.current_note() == "typing a note"         # note being typed untouched
    w.on_new(None); w.face_buttons[5].set_active(True); return False                    # log a newer mood in Moodlog
def s4():
    assert json.load(open(gpath))[today.isoformat()] == 5
    subprocess.run(["import", "-window", "root", "/tmp/link.png"]); app.quit(); return False
for t, f in ((1500, s1), (3000, s2), (9500, s3), (11500, s4)):
    GLib.timeout_add(t, guard(f))
app.run([]); print("ERRORS" if err else "LINK UI OK")
