"""Your diary as a book: which days go in, the periods, and the PDF."""
import os, sys, tempfile, datetime as dt, importlib.machinery, importlib.util
os.environ.update(MOODLOG_DATA_DIR=tempfile.mkdtemp(), MOODLOG_CONFIG_DIR=tempfile.mkdtemp())
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

s, ev = m.Store(""), m.EventStore("")
a, b, c = dt.date(2026, 1, 5), dt.date(2026, 3, 2), dt.date(2026, 3, 9)
s.add(a, "09:00", 3, "")                                  # only a face: not in the book
s.add(b, "09:00", 2, "Rainy.", feelings="tiredness")
s.add(b, "20:00", 4, "", activities="friends")            # tagged, no words: in
s.add(c, "08:00", 1, "Just for me", private=True)
ev.items.append([a, "medication", "New dose", "", "e1", ""])
days = m.book_days(s, ev)
assert [d for d, _e, _v in days] == [a, b], days
assert days[0][1] == [] and len(days[0][2]) == 1          # the event keeps its day
assert len(days[1][1]) == 2
assert [d for d, _e, _v in m.book_days(s, ev, include_private=True)] == [a, b, c]
assert [d for d, _e, _v in m.book_days(s, ev, start=dt.date(2026, 2, 1))] == [b]
today = dt.date(2026, 11, 25)
assert m.book_range(s, "all", today) == (None, today)
assert m.book_range(s, "year", today)[0] == dt.date(2026, 1, 1)
assert m.book_range(s, "3m", today)[0] == today - dt.timedelta(days=90)
path = os.path.join(tempfile.mkdtemp(), "book.pdf")
assert m.export_book(path, s, ev) == 2 and os.path.getsize(path) > 1000
assert m.export_book(path + "2", m.Store(""), m.EventStore("")) == 0 and not os.path.exists(path + "2")
print("book ok")
