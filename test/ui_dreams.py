import os; os.environ["MOODLOG_NO_WHATS_NEW"] = "1"; os.environ["MOODLOG_LANG"] = "en"
import importlib.machinery, importlib.util, datetime as dt, sys
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib, Gtk
app = m.App("dreams"); err = []
today = dt.date.today()
def kids(box):
    out, c = [], box.get_first_child()
    while c: out.append(c); c = c.get_next_sibling()
    return out
def guard(f):
    def run():
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    return run
def s1():
    w = app.get_active_window()
    assert w.stack.get_visible_child_name() == "dreams" and not w.dreams.items
    assert w.dreams_empty.get_visible() and "No dreams yet" in w.dreams_empty.get_text()
    # an empty dream is refused
    win = m.DreamWindow(w, day=today); win.present(); win.on_save()
    assert win.error.get_visible() and not w.dreams.items
    # a bad date is refused
    win.text_view.get_buffer().set_text("Lost in a station"); win.date_entry.set_text("19/11"); win.on_save()
    assert "year-month-day" in win.error.get_text() and not w.dreams.items
    win.date_entry.set_text(today.isoformat())
    win.feeling_drop.set_selected(3)                      # disturbing
    win.tag_buttons["nightmare"].set_active(True); win.tag_buttons["vivid"].set_active(True)
    win.on_save()
    assert len(w.dreams.items) == 1 and w.dreams.items[0][1:4] == ["disturbing", "nightmare,vivid", "Lost in a station"]
    assert open(os.path.join(m.data_dir(), "dreams.csv")).read().count("Lost in a station") == 1
    # a "didn't remember" night with no text is fine
    win = m.DreamWindow(w, day=today - dt.timedelta(days=1)); win.tag_buttons["forgotten"].set_active(True); win.on_save()
    assert len(w.dreams.items) == 2
    w.refresh_dreams()
    assert len(kids(w.dreams_list)) == 2 and w.dream_summary_card.get_visible()
    assert "2 nights with a dream entry, 1 with a nightmare" in w.dream_summary.get_text(), w.dream_summary.get_text()
    # search
    w.dream_search.set_text("station"); w.refresh_dreams(); assert len(kids(w.dreams_list)) == 1
    w.dream_search.set_text("nothing like this"); w.refresh_dreams()
    assert not w.dreams_list.get_visible() and "No dreams match" in w.dreams_empty.get_text()
    w.dream_search.set_text(""); w.refresh_dreams()
    # the day panel lists the dream; the calendar tooltip mentions it
    w.select_day(today)
    assert any(isinstance(c, Gtk.Button) for c in kids(w.day_events)) and w.day_events.get_visible()
    # edit, then delete
    dream = [d for d in w.dreams.items if d[3]][0]
    win = m.DreamWindow(w, dream=dream); win.text_view.get_buffer().set_text("Lost in a big station"); win.on_save()
    assert dream[3] == "Lost in a big station" and len(w.dreams.items) == 2
    win = m.DreamWindow(w, dream=w.dreams.items[0]); win.on_delete()
    assert len(w.dreams.items) == 1 and len(m.DreamStore().deleted) == 1
    # the report window offers the dream options only when there are dreams, text only after dreams
    rep = m.ReportWindow(w)
    assert rep.dreams_check.get_visible() and rep.dream_text_check.get_sensitive()
    rep.dream_text_check.set_active(True)          # ticking the text also ticks dreams
    assert rep.dreams_check.get_active()
    rep.dreams_check.set_active(False)             # unticking dreams unticks both
    assert not rep.dream_text_check.get_active()
    rep.dream_text_check.activate()                # like a click
    start, end = rep.dates()
    rep.write(os.path.join(m.data_dir(), "r.pdf"), start, end)
    import subprocess
    text = subprocess.run(["pdftotext", "-layout", os.path.join(m.data_dir(), "r.pdf"), "-"],
                          capture_output=True, text=True).stdout
    assert "Dream: Lost in a big station" in text, text[-800:]
    rep.close()
    # charts show a Dreams card for the period
    w.stack.set_visible_child_name("charts"); w.refresh()
    assert w.chart_dreams_card.get_visible() and "1 night with a dream entry" in w.chart_dreams.get_text()
    app.quit(); return False
GLib.timeout_add(1200, guard(s1))
app.run([]); print("ERRORS" if err else "DREAMS UI OK")
