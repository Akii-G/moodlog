import importlib.machinery, importlib.util, os, subprocess, sys, datetime as dt
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
app = m.App("log"); errors = []
def rows(w):
    out, c = [], w.entry_list.get_first_child()
    while c: out.append(c); c = c.get_next_sibling()
    return out
def step():
    try:
        w = app.get_active_window(); today = dt.date.today(); now = dt.datetime.now().strftime("%H:%M")
        base = len(w.store.entries(today))                     # sample data: 2 entries today
        assert w.current is None and w.time_entry.get_text() == now and len(rows(w)) == base
        assert not w.delete_btn.get_visible()
        # 1. new entry: pick a face -> entry created with the current time, editor switches to edit mode
        w.face_buttons[4].set_active(True)
        e1 = w.current
        assert e1 is not None and e1[:2] == [now, 4] and len(w.store.entries(today)) == base + 1
        assert w.delete_btn.get_visible() and w.new_btn.get_visible() and len(rows(w)) == base + 1
        w.face_buttons[2].set_active(True)                      # changing the face edits the same entry
        assert w.current is e1 and e1[1] == 2 and w.current_mood() == 2 and len(w.store.entries(today)) == base + 1
        w.note_view.get_buffer().set_text('line one, "quoted"\nline two')
        assert e1[2].startswith("line one")
        # 2. another entry at a typed time
        w.on_new(None); assert w.current is None and w.current_mood() == 0 and w.current_note() == ""
        w.time_entry.set_text("7:05")                           # loose format is accepted
        w.face_buttons[5].set_active(True)
        e2 = w.current
        assert e2 is not e1 and e2[:2] == ["07:05", 5]
        times = [e[0] for e in w.store.entries(today)]; assert times == sorted(times)      # kept in time order
        # 3. edit its time: invalid text is flagged and ignored, valid text re-sorts
        w.time_entry.set_text("99:99"); assert w.time_entry.has_css_class("error") and e2[0] == "07:05"
        w.time_entry.emit("activate"); assert e2[0] == "07:05" and w.time_entry.get_text() == "07:05"   # reverted
        w.time_entry.set_text("23.59"); assert e2[0] == "07:05"                                         # not applied while typing
        w.time_entry.emit("activate"); assert not w.time_entry.has_css_class("error") and e2[0] == "23:59"
        assert w.store.entries(today)[-1] is e2 and w.store.latest_mood(today) == 5
        w.flush()
        saved = m.Store().entries(today)
        assert ["23:59", 5, ""] in saved and [now, 2, 'line one, "quoted"\nline two'] in saved, saved
        # 4. click a row in the list -> that entry is loaded into the editor
        w.on_entry_row(None, [r for r in rows(w) if r.entry is e1][0])
        assert w.current is e1 and w.current_mood() == 2 and w.time_entry.get_text() == now
        # 5. emptying an entry removes it; deleting works; calendar count follows
        w.face_buttons[2].set_active(False); w.note_view.get_buffer().set_text("")
        assert w.current is None and all(e is not e1 for e in w.store.entries(today))
        w.on_entry_row(None, [r for r in rows(w) if r.entry is e2][0]); w.on_delete(None)
        assert len(w.store.entries(today)) == base and w.current is None
        w.flush(); assert len(m.Store().entries(today)) == base
        # 6. past day opens on its last entry; a new past-day entry has no time unless typed
        past = today - dt.timedelta(days=40)
        while not w.store.entries(past): past -= dt.timedelta(days=1)
        w.select_day(past); assert w.current is w.store.entries(past)[-1]
        w.on_new(None); assert w.time_entry.get_text() == ""
        w.face_buttons[3].set_active(True); assert w.current[0] == "" and w.store.entries(past)[0] is w.current
        w.on_delete(None)
        # 7. month nav clamps at the current month; charts handle every range
        w.shift(5); assert (w.cursor.year, w.cursor.month) == (today.year, today.month)
        w.shift(-14); w.shift(1); w.select_day(today)
        w.stack.set_visible_child_name("charts")
        for i in (1, 2, 0): w.range_drop.set_selected(i)
        w.stack.set_visible_child_name("notes"); w.search.set_text("walk")
        note_rows = []; c = w.notes_list.get_first_child()
        while c: note_rows.append(c); c = c.get_next_sibling()
        assert note_rows and all(hasattr(r, "entry") for r in note_rows)
        w.on_note_row(None, note_rows[0]); assert w.current is note_rows[0].entry and w.selected == note_rows[0].day
        w.select_day(today); w.stack.set_visible_child_name("log")
        w.on_entry_row(None, rows(w)[-1])
    except Exception:
        import traceback; traceback.print_exc(); errors.append(1); app.quit()
    return False
def shot():
    subprocess.run(["import", "-window", "root", "/tmp/v13_log.png"])
    w = app.get_active_window(); w.set_default_size(980, 1420); w.stack.set_visible_child_name("charts")
    GLib.timeout_add(1500, shot2); return False
def shot2():
    subprocess.run(["import", "-window", "root", "/tmp/v13_charts.png"]); app.quit(); return False
GLib.timeout_add(1500, step); GLib.timeout_add(3500, shot)
app.run([]); print("ERRORS" if errors else "EDITOR OK")
