#!/usr/bin/env python3
"""Copies the iPhone and Android changelogs from src/moodlog (IPHONE_CHANGELOG and
ANDROID_CHANGELOG, the single source) into each phone app, with the Italian from po/it.po,
for their "What's new" screen.

Usage: tools/phone_changelog.py           writes the two changelog.json files
       tools/phone_changelog.py --check   fails if they are out of date or a translation is missing
"""
import importlib.machinery, importlib.util, json, os, re, sys, tempfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = {"IPHONE_CHANGELOG": os.path.join(ROOT, "ios", "Moodlog", "changelog.json"),
       "ANDROID_CHANGELOG": os.path.join(ROOT, "android", "app", "src", "main", "assets", "changelog.json")}


def catalog():
    po = open(os.path.join(ROOT, "po", "it.po"), encoding="utf-8").read()
    lit = r'"(?:[^"\\]|\\.)*"'
    out = {}
    for m in re.finditer(r'msgid ((?:%s\s*)+)msgstr ((?:%s\s*)+)' % (lit, lit), po):
        k = "".join(json.loads(x) for x in re.findall(lit, m.group(1)))
        v = "".join(json.loads(x) for x in re.findall(lit, m.group(2)))
        if k and v:
            out[k] = v
    return out


def build():
    os.environ.setdefault("MOODLOG_DATA_DIR", tempfile.mkdtemp())
    loader = importlib.machinery.SourceFileLoader("moodlog_src", os.path.join(ROOT, "src", "moodlog"))
    spec = importlib.util.spec_from_loader("moodlog_src", loader)
    m = importlib.util.module_from_spec(spec)
    loader.exec_module(m)
    it = catalog()
    files, missing = {}, []
    for name, path in OUT.items():
        versions = []
        for version, date, items in getattr(m, name):
            for i in items:
                if i not in it:
                    missing.append(i)
            versions.append({"version": version, "date": date,
                             "items": {"en": list(items), "it": [it.get(i, i) for i in items]}})
        files[path] = json.dumps(versions, ensure_ascii=False, indent=1) + "\n"
    return files, missing


if __name__ == "__main__":
    files, missing = build()
    for i in missing:
        print("missing Italian:", i)
    if "--check" in sys.argv:
        stale = [p for p, text in files.items()
                 if not os.path.exists(p) or open(p, encoding="utf-8").read() != text]
        for p in stale:
            print("out of date:", os.path.relpath(p, ROOT))
        sys.exit(1 if stale or missing else 0)
    for p, text in files.items():
        open(p, "w", encoding="utf-8").write(text)
    print("wrote %d files" % len(files))
    sys.exit(1 if missing else 0)
