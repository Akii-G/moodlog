"""Search: notes, feeling words and events, ignoring capitals and accents; matches in bold."""
import datetime as dt, os, tempfile, importlib.machinery, importlib.util
data = tempfile.mkdtemp(); cfgdir = tempfile.mkdtemp()
os.environ.update(MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=cfgdir, MOODLOG_LANG="en")
here = os.path.dirname(os.path.abspath(__file__))
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(here, "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

assert m.fold("Perché ÀNSIA") == "perche ansia"
assert m.find_matches("Perché no? perche sì", "PERCHE") == [(0, 6), (11, 17)]
assert m.find_matches("Straße", "strasse") == [(0, 6)]        # one letter that folds to two
assert m.find_matches("anything", "  ") == []
assert m.marked("a <b> & Café", "cafe") == "a &lt;b&gt; &amp; <b>Café</b>"

s, ev = m.Store(""), m.EventStore("")
d1, d2, d3 = dt.date(2026, 11, 1), dt.date(2026, 11, 2), dt.date(2026, 11, 3)
walk = s.add(d1, "09:00", 4, "Long walk by the sea")
tired = s.add(d2, "08:00", 2, "", feelings="tiredness")      # no note, only a feeling word
late = s.add(d2, "22:00", 3, "Walked home, tired")
blank = s.add(d3, "10:00", 3, "")
exam = ev.add(d3, "life", "Exam", "Maths, went better than expected")

# no query: every note, newest first; entries without a note are left out
assert [r[2] for r in m.search_results(s, ev, "")] == [late, walk]
# a word in notes
assert [r[2] for r in m.search_results(s, ev, "WALK")] == [late, walk]
# a feeling word finds the entry that only has the feeling
assert [r[2] for r in m.search_results(s, ev, "tired")] == [late, tired]
# events: title or note, newest first among the rest
got = m.search_results(s, ev, "maths")
assert got == [("event", d3, exam)], got
assert m.search_results(s, ev, "nothing like this") == []
print("search ok")
