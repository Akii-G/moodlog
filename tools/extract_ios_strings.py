#!/usr/bin/env python3
"""Lists every text of the iPhone app that can be translated, and checks it.lproj.

Usage: tools/extract_ios_strings.py           prints the texts
       tools/extract_ios_strings.py --check   lists texts missing from it.lproj (exit 1 if any)
"""
import glob, os, re, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ios", "Moodlog")
LIT = r'"((?:[^"\\]|\\.)*)"'
SWIFTUI = (r'\b(?:Text|Label|Button|Section|Toggle|DatePicker|Picker|TextField|SecureField|'
           r'LabeledContent|NavigationLink|navigationTitle|accessibilityLabel|tile|card|empty)\(\s*' + LIT)
PATTERNS = [
    re.compile(r'\btrn\(\s*' + LIT + r'\s*,\s*' + LIT),          # both plural forms
    re.compile(r'\btr\(\s*' + LIT),
    re.compile(r'\btr\([^()"]*\?\s*' + LIT + r'\s*:\s*' + LIT),  # tr(a ? "x" : "y")
    re.compile(SWIFTUI),
    re.compile(r'\.value\(\s*' + LIT),
    re.compile(r'ScalePicker\(title:\s*' + LIT),
]
NAMES = re.compile(r'static let names = \[([^\]]*)\]')   # mood names, translated via tr($0)
SKIP = {"Moodlog", "--:--", "–"}


def extract():
    keys = []
    for path in sorted(glob.glob(os.path.join(ROOT, "**", "*.swift"), recursive=True)):
        src = open(path, encoding="utf-8").read()
        for block in NAMES.findall(src):
            for k in re.findall(LIT, block):
                if k not in keys:
                    keys.append(k)
        for pat in PATTERNS:
            for m in pat.finditer(src):
                for k in m.groups():
                    if k and "\\(" not in k and k not in SKIP and k not in keys:
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
    if "--check" in sys.argv:
        have = read_strings(os.path.join(ROOT, "it.lproj", "Localizable.strings"))
        missing = [k for k in keys if k not in have]
        for k in missing: print("missing:", k)
        extra = [k for k in have if k not in keys]
        for k in extra: print("unused:", k)
        print("%d texts, %d missing, %d unused" % (len(keys), len(missing), len(extra)))
        sys.exit(1 if missing else 0)
    for k in keys: print(k)
