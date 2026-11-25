"""Your diary as a book: the Export menu opens it, it counts the days with words, and saves a PDF."""
import os, sys, tempfile, datetime as dt, importlib.machinery, importlib.util
os.environ.update(MOODLOG_NO_WHATS_NEW="1", MOODLOG_DATA_DIR=tempfile.mkdtemp(),
                  MOODLOG_CONFIG_DIR=tempfile.mkdtemp())
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
d = dt.date.today() - dt.timedelta(days=3)
s = m.Store(); s.add(d, "09:00", 2, "A slow morning.", activities="walk"); s.add(d, "21:00", 3, "")
s.add(d - dt.timedelta(days=1), "10:00", 4, "Only for me", private=True); s.save()
app = m.App(); err = []
out = os.path.join(tempfile.mkdtemp(), "diary")

def check():
    try:
        win = app.get_active_window()
        win.activate_action("win.book", None)
        book = [w for w in m.Gtk.Window.list_toplevels() if isinstance(w, m.BookWindow)][0]
        assert book.private_check.get_visible()
        assert len(book.days()) == 1 and book.save.get_sensitive()
        book.private_check.set_active(True)
        assert len(book.days()) == 2
        book.private_check.set_active(False)
        start, end = m.book_range(win.store, book.period)
        book.write(out, start, end)
        assert os.path.getsize(out + ".pdf") > 1000
    except Exception as e:
        import traceback; traceback.print_exc(); err.append(e)
    app.quit()
    return False

GLib.timeout_add(1200, check)
app.run([])
print("FAIL" if err else "ui book ok")
sys.exit(1 if err else 0)
