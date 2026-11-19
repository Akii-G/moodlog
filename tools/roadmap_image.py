#!/usr/bin/env python3
"""Draws docs/roadmap.png, the timeline shown at the bottom of the README."""
import sys, cairo, gi
gi.require_version("Pango", "1.0"); gi.require_version("PangoCairo", "1.0")
from gi.repository import Pango, PangoCairo

S = 2                      # drawn at 2x for sharp text on GitHub
W, H = 1400, 548
INK, INK2, MUTED, LINE = "#1b1b1b", "#4a4945", "#6d6b66", "#d9d8d2"
DONE, NOW, LATER = "#1baf7a", "#2a78d6", "#bdbbb3"

STAGES = [
    ("Done", "November 2026", DONE, "Where we are", [
        "PC app 1.0 \u2192 2.1", "iPhone app 1.0", "Doctor report and events",
        "Energy, anxiety and sleep", "Private sync, no cloud"]),
    ("Step 1", "Now", NOW, "Solid for its users", [
        "Italian, then more languages", "First-run welcome",
        "Plain privacy policy", "Accessibility"]),
    ("Step 2", "Next", LATER, "Every Linux distro", [
        "Flatpak on Flathub", "Install from any distro's software centre"]),
    ("Step 3", "Later", LATER, "iPhone & Mac stores", [
        "Pick the store name", "Apple developer account", "iCloud sync",
        "iPhone on the App Store", "Mac app (same SwiftUI code)"]),
    ("Step 4", "Later", LATER, "Windows", [
        "Package or native app", "Microsoft Store",
        "Same data files and sync"]),
]


def rgb(h):
    h = h.lstrip("#"); return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def text(cr, s, x, y, size, color, weight="Regular", width=None, align=Pango.Alignment.LEFT):
    lay = PangoCairo.create_layout(cr)
    lay.set_font_description(Pango.FontDescription("Inter %s %s" % (weight, size)))
    lay.set_text(s, -1)
    if width:
        lay.set_width(int(width * Pango.SCALE)); lay.set_alignment(align)
        lay.set_wrap(Pango.WrapMode.WORD_CHAR)
    cr.set_source_rgb(*rgb(color)); cr.move_to(x, y); PangoCairo.show_layout(cr, lay)
    return lay.get_pixel_size()[1]


def rounded(cr, x, y, w, h, r):
    cr.new_sub_path()
    cr.arc(x + w - r, y + r, r, -1.5708, 0); cr.arc(x + w - r, y + h - r, r, 0, 1.5708)
    cr.arc(x + r, y + h - r, r, 1.5708, 3.1416); cr.arc(x + r, y + r, r, 3.1416, 4.7124)
    cr.close_path()


def main(out):
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, W * S, H * S)
    cr = cairo.Context(surf); cr.scale(S, S)
    rounded(cr, 0, 0, W, H, 24); cr.set_source_rgb(*rgb("#ffffff")); cr.fill()
    text(cr, "Moodlog roadmap", 48, 40, 26, INK, "Bold")
    text(cr, "Goal: help people who track their mood for their health, on every device they use.",
         48, 84, 13, MUTED)

    n = len(STAGES); left, right = 48, W - 48
    col = (right - left) / n; y_line = 205
    # timeline: solid up to "Now", dashed after
    cx = [left + col * i + col / 2 for i in range(n)]
    cr.set_line_width(4); cr.set_line_cap(cairo.LINE_CAP_ROUND)
    cr.set_source_rgb(*rgb(DONE)); cr.move_to(cx[0], y_line); cr.line_to(cx[1], y_line); cr.stroke()
    cr.set_source_rgb(*rgb(LINE)); cr.set_dash([2, 10])
    cr.move_to(cx[1], y_line); cr.line_to(cx[-1], y_line); cr.stroke(); cr.set_dash([])

    for i, (step, when, color, title, items) in enumerate(STAGES):
        x = cx[i]
        # node with a 3px white ring so it sits cleanly on the line
        cr.arc(x, y_line, 15, 0, 6.2832); cr.set_source_rgb(1, 1, 1); cr.fill()
        cr.arc(x, y_line, 12, 0, 6.2832); cr.set_source_rgb(*rgb(color)); cr.fill()
        if i == 0:   # tick
            cr.set_source_rgb(1, 1, 1); cr.set_line_width(2.6)
            cr.move_to(x - 5, y_line); cr.line_to(x - 1.5, y_line + 4); cr.line_to(x + 5.5, y_line - 4.5)
            cr.stroke()
        elif i == 1:  # "now" pulse ring
            cr.arc(x, y_line, 20, 0, 6.2832); cr.set_source_rgba(*rgb(NOW), 0.25)
            cr.set_line_width(4); cr.stroke()
        text(cr, step.upper(), x - col / 2, y_line - 46, 10, MUTED, "SemiBold", col, Pango.Alignment.CENTER)
        text(cr, when, x - col / 2, y_line + 28, 11, INK2 if i > 1 else color, "SemiBold",
             col, Pango.Alignment.CENTER)

        # card
        cw, top = col - 20, y_line + 64
        ch = H - top - 36
        rounded(cr, x - cw / 2, top, cw, ch, 16)
        cr.set_source_rgb(*rgb("#f4f3ef")); cr.fill()
        cr.rectangle(x - cw / 2 + 18, top + 18, 28, 4); cr.set_source_rgb(*rgb(color)); cr.fill()
        y = top + 32
        y += text(cr, title, x - cw / 2 + 18, y, 15, INK, "Bold", cw - 36) + 12
        for item in items:
            cr.arc(x - cw / 2 + 22, y + 10, 2.5, 0, 6.2832); cr.set_source_rgb(*rgb(MUTED)); cr.fill()
            y += text(cr, item, x - cw / 2 + 32, y, 12, INK2, "Regular", cw - 50) + 8

    surf.write_to_png(out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "docs/roadmap.png")
