#!/usr/bin/env python3
"""Lists every text of the Android app that can be translated, and checks the Italian file.

Usage: tools/extract_android_strings.py           prints the texts
       tools/extract_android_strings.py --check   lists texts missing from it.strings (exit 1 if any)
       tools/extract_android_strings.py --prune   also removes texts the app no longer uses
"""
import glob, os, re, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "android", "app", "src", "main")
IT = os.path.join(ROOT, "assets", "i18n", "it.strings")
LIT = r'"((?:[^"\\]|\\.)*)"'
PATTERNS = [
    re.compile(r'\btrn\(\s*' + LIT + r'\s*,\s*' + LIT),
    re.compile(r'\btr\(\s*' + LIT),
    re.compile(r'\btr\(\s*if \([^)]*\)\s*' + LIT + r'\s*else\s*' + LIT),   # tr(if (x) "a" else "b")
    re.compile(r'\bScalePicker\(\s*' + LIT),
]
NAMES = re.compile(r'val names = mapOf\(([^)]*)\)')     # mood names, translated via tr(it)
SKIP = {"Moodlog", "--:--"}


def kotlin(s):
    return s.replace("\\$", "$")


def extract():
    keys = []
    for path in sorted(glob.glob(os.path.join(ROOT, "java", "**", "*.kt"), recursive=True)):
        src = open(path, encoding="utf-8").read()
        for block in NAMES.findall(src):
            for k in re.findall(LIT, block):
                if k not in keys: keys.append(k)
        for pat in PATTERNS:
            for m in pat.finditer(src):
                for k in m.groups():
                    k = kotlin(k) if k else k
                    if k and "${" not in k and k not in SKIP and k not in keys:
                        keys.append(k)
    return keys


def read_strings(path):
    out = {}
    if os.path.exists(path):
        for m in re.finditer(LIT + r'\s*=\s*' + LIT + r'\s*;', open(path, encoding="utf-8").read()):
            out[m.group(1)] = m.group(2)
    return out


if __name__ == "__main__":
    keys = extract()
    if "--check" in sys.argv or "--prune" in sys.argv:
        have = read_strings(IT)
        missing = [k for k in keys if k not in have]
        for k in missing: print("missing:", k)
        extra = [k for k in have if k not in keys]
        if "--prune" in sys.argv and extra:
            text = open(IT, encoding="utf-8").read()
            for k in extra:
                text = re.sub(r'^"' + re.escape(k) + r'"\s*=\s*' + LIT + r'\s*;\n', "", text, flags=re.M)
            open(IT, "w", encoding="utf-8").write(text)
            print("removed %d unused" % len(extra))
        print("%d texts, %d missing, %d unused" % (len(keys), len(missing), len(extra)))
        sys.exit(1 if missing else 0)
    for k in keys: print(k)
