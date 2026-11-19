#!/usr/bin/env python3
"""Lists every text of the PC app that can be translated, as po/moodlog.pot.

Usage: tools/extract_strings.py            writes po/moodlog.pot
       tools/extract_strings.py --check    lists texts missing from po/it.po (exit 1 if any)
"""
import ast, json, os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "src", "moodlog")


def extract():
    tree = ast.parse(open(SRC, encoding="utf-8").read())
    seen, out = set(), []

    def add(key, line):
        if key not in seen:
            seen.add(key); out.append((key, [line]))
        else:
            for k, lines in out:
                if k == key: lines.append(line)

    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            args = node.args
            if node.func.id == "tr" and args and isinstance(args[0], ast.Constant) and isinstance(args[0].value, str):
                add(args[0].value, node.lineno)
            elif node.func.id == "trn" and len(args) >= 2 and all(
                    isinstance(a, ast.Constant) and isinstance(a.value, str) for a in args[:2]):
                add((args[0].value, args[1].value), node.lineno)
        # changelog entries are translated when shown in the What's new window
        if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "CHANGELOG"
                                                 for t in node.targets):
            for version in node.value.elts:
                for item in version.elts[2].elts:
                    add(item.value, item.lineno)
    out.sort(key=lambda e: min(e[1]))
    return out


def q(text):
    return json.dumps(text, ensure_ascii=False)


def main():
    entries = extract()
    if "--check" in sys.argv:
        sys.path.insert(0, ROOT)
        import importlib.machinery, importlib.util
        loader = importlib.machinery.SourceFileLoader("moodlog_po", SRC)
        spec = importlib.util.spec_from_loader("moodlog_po", loader)
        mod = importlib.util.module_from_spec(spec); loader.exec_module(mod)
        cat = mod.read_po(os.path.join(ROOT, "po", "it.po"))
        missing = [k for k, _l in entries if k not in cat]
        for k in missing: print("missing:", k)
        print("%d texts, %d missing" % (len(entries), len(missing)))
        return 1 if missing else 0
    lines = ['msgid ""', 'msgstr ""', '"Content-Type: text/plain; charset=UTF-8\\n"',
             '"Plural-Forms: nplurals=2; plural=(n != 1);\\n"', ""]
    for key, refs in entries:
        lines.append("#: src/moodlog:" + " src/moodlog:".join(str(r) for r in sorted(refs)[:3]))
        if isinstance(key, tuple):
            lines += ["msgid " + q(key[0]), "msgid_plural " + q(key[1]),
                      'msgstr[0] ""', 'msgstr[1] ""', ""]
        else:
            lines += ["msgid " + q(key), 'msgstr ""', ""]
    open(os.path.join(ROOT, "po", "moodlog.pot"), "w", encoding="utf-8").write("\n".join(lines))
    print("%d texts" % len(entries))


if __name__ == "__main__":
    sys.exit(main())
