"""The welcome tour: shown once on a brand-new install, never over existing data,
steps with the keys, remembers that it was seen, and opens again from the ☰ menu."""
import os, json, datetime as dt
os.environ.pop("MOODLOG_NO_WHATS_NEW", None); os.environ["MOODLOG_LANG"] = "en"
import importlib.machinery, importlib.util
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib, Gtk, Gdk
assert not m.Store().days, "run with an empty data folder"
app = m.App("charts"); err = []
cfg_path = os.path.join(m.config_dir(), "config.json")
key = lambda w, name: w.tour._key(None, Gdk.keyval_from_name(name), 0, 0)
def guard(f):
    def run():
        try: return f()
        except Exception:
            import traceback; traceback.print_exc(); err.append(1); app.quit(); return False
    return run
def s1():
    w = app.get_active_window()
    assert w.tour is not None, "no tour on a brand-new install"
    assert w.stack.get_visible_child_name() == "log", "the tour starts on the Log page"
    assert w.tour.step == 0 and w.tour.face.get_visible() and not w.tour.back.get_visible()
    assert "No pressure" in w.tour.body.get_text()
    return False
def s2():
    w = app.get_active_window(); t = w.tour
    # every step that points at something has a spotlight on screen
    for i, (target, _title, _body) in enumerate(m.TOUR_STEPS):
        t.go(i)
        if target and target != "customize":   # customize: options in the card, no spotlight
            hole = t.target(target)
            assert hole and hole[2] > 10 and hole[3] > 10, (target, hole)
            assert 0 <= hole[0] and hole[0] + hole[2] <= t.get_width() + 8, (target, hole)
        if target == "customize":
            assert t.extra.get_visible(), "the customize step shows its options"
        cw, ch = t.card_size()
        x, y = t.card_place(t.target(target))
        assert x >= 0 and y >= 0 and x + cw <= t.get_width() + 1 and y + ch <= t.get_height() + 1, (target, x, y, cw, ch)
    # the card's size must not depend on where it sits (that made it jump back and forth)
    t.go(2)
    sizes = set()
    for mx, my in ((0, 0), (500, 300), (16, 40)):
        t.card.set_margin_start(mx); t.card.set_margin_top(my); sizes.add(t.card_size())
    assert len(sizes) == 1, sizes
    first = t.card_place(t.target("calendar"))
    t._place = (None,)
    assert t.card_place(t.target("calendar")) == first
    t.go(3); key(w, "Left"); assert t.step == 2; key(w, "Right"); assert t.step == 3
    t.go(len(m.TOUR_STEPS) - 1)
    assert t.next.get_label() == "Let's start" and not t.skip.get_visible() and t.face.get_visible()
    key(w, "Right")
    assert w.tour is None, "the last Next closes the tour"
    assert json.load(open(cfg_path)).get("tour_done") is True
    # it doesn't come back by itself, but the menu opens it again; Escape closes it
    w.show_changelog_after_update(); assert w.tour is None
    w.activate_action("win.tour", None); assert w.tour is not None and w.tour.step == 0
    key(w, "Escape"); assert w.tour is None
    # someone who already has data (an update from before 2.1) gets What's new, not the tour
    json.dump({}, open(cfg_path, "w"))
    w.store.add(dt.date.today(), "09:00", 4, "", 0, 0)
    w.show_changelog_after_update()
    assert w.tour is None
    assert [x for x in Gtk.Window.list_toplevels() if isinstance(x, m.ChangelogWindow) and x.get_visible()]
    app.quit(); return False
GLib.timeout_add(1500, guard(s1)); GLib.timeout_add(2300, guard(s2))
app.run([]); print("ERRORS" if err else "TOUR OK")
