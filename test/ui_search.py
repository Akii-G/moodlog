"""The Notes tab's search: notes, feeling words and events; a result opens its day or event."""
import os, sys, tempfile, datetime as dt, importlib.machinery, importlib.util
os.environ.update(MOODLOG_NO_WHATS_NEW="1", MOODLOG_DATA_DIR=tempfile.mkdtemp(),
                  MOODLOG_CONFIG_DIR=tempfile.mkdtemp(), MOODLOG_LANG="it")
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
d = dt.date.today() - dt.timedelta(days=3)
s = m.Store(); s.add(d, "09:00", 2, "Perché sono così stanco?"); s.add(d, "21:00", 3, "", feelings="tiredness"); s.save()
ev = m.EventStore(); ev.add(d, "medication", "Nuova dose", "Da 50 a 75 mg"); ev.save()
app = m.App("notes"); err = []

def rows(win):
    out, c = [], win.notes_list.get_first_child()
    while c: out.append(c); c = c.get_next_sibling()
    return out

def check():
    try:
        win = app.get_active_window()
        assert [r.kind for r in rows(win)] == ["entry"], "no query: only the note"
        win.search.set_text("PERCHE")
        win.refresh_notes()
        assert [r.kind for r in rows(win)] == ["entry"]
        win.search.set_text("stanch")          # the feeling word, in Italian
        win.refresh_notes()
        assert [r.kind for r in rows(win)] == ["entry"] and rows(win)[0].item[1] == 3
        win.search.set_text("dose")
        win.refresh_notes()
        assert [r.kind for r in rows(win)] == ["event"]
        win.search.set_text("niente del genere")
        win.refresh_notes()
        assert not rows(win) and win.notes_empty.get_visible()
        win.search.set_text("stanco")
        win.refresh_notes()
        win.on_note_row(None, rows(win)[0])
        assert win.stack.get_visible_child_name() == "log" and win.selected == d
    except Exception as e:
        import traceback; traceback.print_exc(); err.append(e)
    app.quit()
    return False

GLib.timeout_add(1200, check)
app.run([])
print("FAIL" if err else "ui search ok")
sys.exit(1 if err else 0)
