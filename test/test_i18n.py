"""Languages: .po reading, language choice, plurals, dates, and that it.po covers every text."""
import datetime as dt, importlib.machinery, importlib.util, json, os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "moodlog")


def load(lang, **env):
    os.environ.update(env)
    os.environ["MOODLOG_LANG"] = lang
    loader = importlib.machinery.SourceFileLoader("moodlog_" + lang, SRC)
    spec = importlib.util.spec_from_loader("moodlog_" + lang, loader)
    mod = importlib.util.module_from_spec(spec); loader.exec_module(mod)
    return mod


os.environ["MOODLOG_CONFIG_DIR"] = tempfile.mkdtemp()
en, it = load("en"), load("it")
assert en.LANG_CODE == "en" and it.LANG_CODE == "it"

# texts
assert en.MOOD_NAMES[3] == "Okay" and it.MOOD_NAMES[3] == "Così così"
assert it.tr("Save") == "Salva" and it.tr("not a known text") == "not a known text"
assert it.trn("%d day", "%d days", 1) % 1 == "1 giorno"
assert it.trn("%d day", "%d days", 5) % 5 == "5 giorni"
assert en.trn("%d day", "%d days", 1) % 1 == "1 day" and en.trn("%d day", "%d days", 2) % 2 == "2 days"

# dates follow the app's language, not the system's
d = dt.date(2026, 11, 16)
assert en.fmt_date(d, en.tr("%A, %-d %B %Y")) == "Monday, 16 November 2026"
assert it.fmt_date(d, it.tr("%A, %-d %B %Y")) == "Lunedì 16 novembre 2026"
assert it.fmt_date(d, "%a %-d %b") == "Lun 16 nov"
assert it.fmt_date(dt.date(2026, 5, 3), "%-d %b %Y") == "3 mag 2026"

# language choice: env > config > system
for value, want in (("it_IT.UTF-8", "it"), ("en_GB.UTF-8", "en"), ("C.UTF-8", "en"), ("de_DE", "de")):
    os.environ.pop("LANGUAGE", None); os.environ.pop("LC_ALL", None); os.environ.pop("LC_MESSAGES", None)
    os.environ["LANG"] = value
    assert en.system_language() == want, (value, en.system_language())
cfg = tempfile.mkdtemp()
json.dump({"language": "it"}, open(os.path.join(cfg, "config.json"), "w"))
os.environ["MOODLOG_CONFIG_DIR"] = cfg
os.environ.pop("MOODLOG_LANG")
assert en.configured_language() == "it" and en.pick_language() == "it"
os.environ["LANG"] = "de_DE.UTF-8"
json.dump({"language": "auto"}, open(os.path.join(cfg, "config.json"), "w"))
assert en.pick_language() == "en", "unsupported system language falls back to English"

# the .po reader: plurals, continuation lines, fuzzy entries skipped
po = os.path.join(tempfile.mkdtemp(), "x.po")
open(po, "w", encoding="utf-8").write('''msgid ""
msgstr "Content-Type: text/plain; charset=UTF-8\\n"

msgid "One"
msgstr "Uno"

#, fuzzy
msgid "Two"
msgstr "Due?"

msgid "%d cat"
msgid_plural "%d cats"
msgstr[0] "%d gatto"
msgstr[1] "%d gatti"

msgid "Long "
"text"
msgstr "Testo "
"lungo"
''')
cat = en.read_po(po)
assert cat == {"One": "Uno", ("%d cat", "%d cats"): ["%d gatto", "%d gatti"], "Long text": "Testo lungo"}, cat

# every text in the app has an Italian translation
r = subprocess.run([sys.executable, os.path.join(HERE, "..", "tools", "extract_strings.py"), "--check"],
                   capture_output=True, text=True)
assert r.returncode == 0, r.stdout
print("I18N OK")
