import os, sys, tempfile, shutil, subprocess, datetime as dt, time
root = tempfile.mkdtemp()
os.environ["MOODLOG_CONFIG_DIR"] = os.path.join(root, "config"); os.environ.pop("MOODLOG_DATA_DIR", None)
os.environ["XDG_DATA_HOME"] = os.path.join(root, "xdg"); os.environ["MOODLOG_GRIDGETS_DIR"] = "/nonexistent"
src = sys.argv[1]
os.makedirs(os.path.join(root, "xdg", "moodlog")); shutil.copy(os.path.join(src, "moods.csv"), os.path.join(root, "xdg", "moodlog"))
import importlib.machinery, importlib.util
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
app = m.App("log"); err = []; today = dt.date.today()
def guard(f):
    def run():
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    return run
def s1():
    w = app.get_active_window()
    for a in ("open-folder", "backup", "restore", "data-folder"): assert w.lookup_action(a), a
    before = len(w.store.days)
    dwin = m.DataWindow(w); dwin.present(); app.dwin = dwin
    sync = os.path.join(root, "Sync", "Moodlog"); app.sync = sync
    dwin.switch(sync, copy=True)                                   # 1. move to an empty sync folder
    assert m.data_dir() == sync and os.path.exists(os.path.join(sync, "moods.csv"))
    assert len(w.store.days) == before and os.path.exists(os.path.join(root, "xdg", "moodlog", "moods.csv"))
    # 2. another computer writes to the synced file -> picked up by the poll
    other = m.Store(os.path.join(sync, "moods.csv")); other.add(today, "06:30", 1, "from the laptop", 2, 4)
    time.sleep(0.05); other.save()
    return False
def s2():
    w = app.get_active_window()
    assert any(e[2] == "from the laptop" for e in w.store.entries(today)), "outside change reloaded"
    # 3. a Syncthing conflict copy -> banner, then merge
    c = m.Store(os.path.join(app.sync, "moods.sync-conflict-20261118-111111-XYZ.csv"))
    c.add(today - dt.timedelta(days=2), "21:00", 5, "conflicted entry"); c.save()
    return False
def s3():
    w = app.get_active_window()
    assert w.banner.get_visible() and "conflict" in w.banner_label.get_text()
    subprocess.run(["import", "-window", "root", "/tmp/data_banner.png"])
    w.merge_conflict_copies()
    assert not w.banner.get_visible() and m.conflict_files() == []
    assert any(e[2] == "conflicted entry" for e in w.store.entries(today - dt.timedelta(days=2)))
    # 4. backup + restore through the window
    bk = os.path.join(root, "bk.zip"); m.make_backup(bk)
    w.select_day(today); w.on_new(None); w.face_buttons[3].set_active(True); w.flush()
    n = len(w.store.entries(today)); w.do_restore(bk)
    assert len(w.store.entries(today)) == n - 1 and os.listdir(os.path.join(app.sync, "backups"))
    # 5. second computer: its own data + the synced folder -> merge
    pc2 = os.path.join(root, "pc2-default"); os.makedirs(pc2)
    p2 = m.Store(os.path.join(pc2, "moods.csv")); p2.add(today - dt.timedelta(days=1), "10:00", 4, "pc2 note"); p2.save()
    m.save_config({"data_dir": pc2}); w.reload_data()
    app.dwin.switch(app.sync, merge=True)
    assert m.data_dir() == app.sync
    assert any(e[2] == "pc2 note" for e in m.Store().entries(today - dt.timedelta(days=1)))
    assert any(e[2] == "from the laptop" for e in m.Store().entries(today))
    assert any(n.startswith("before-merge") for n in os.listdir(os.path.join(app.sync, "backups")))
    app.dwin.update(); app.dwin.present()
    return False
def s4():
    subprocess.run(["import", "-window", "root", "/tmp/data_window.png"]); app.quit(); return False
for t, f in ((1500, s1), (8000, s2), (14500, s3), (16500, s4)):
    GLib.timeout_add(t, guard(f))
app.run([]); print("ERRORS" if err else "DATA UI OK")
