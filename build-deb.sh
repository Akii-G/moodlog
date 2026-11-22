#!/bin/sh
# Builds sottovoce_<version>_all.deb in the current folder. The package was called moodlog
# before 3.0: it replaces it, and keeps the moodlog command and paths so nothing breaks.
set -e
cd "$(dirname "$0")"
VERSION=$(sed -n 's/^VERSION = "\(.*\)"/\1/p' src/moodlog)
PKG=build/sottovoce_${VERSION}_all
rm -rf build && mkdir -p "$PKG/DEBIAN"
install -Dm755 src/moodlog "$PKG/usr/bin/moodlog"
ln -s moodlog "$PKG/usr/bin/sottovoce"
install -Dm644 data/app.moodlog.Moodlog.desktop "$PKG/usr/share/applications/app.moodlog.Moodlog.desktop"
install -Dm644 data/app.moodlog.Moodlog.svg "$PKG/usr/share/icons/hicolor/scalable/apps/app.moodlog.Moodlog.svg"
install -Dm644 data/moodlog-sync.service "$PKG/usr/lib/systemd/user/moodlog-sync.service"
install -Dm644 data/moodlog-reminder.service "$PKG/usr/lib/systemd/user/moodlog-reminder.service"
for po in po/*.po; do install -Dm644 "$po" "$PKG/usr/share/moodlog/po/$(basename "$po")"; done
install -Dm644 LICENSE "$PKG/usr/share/doc/moodlog/copyright"
install -Dm644 README.md "$PKG/usr/share/doc/moodlog/README.md"
SIZE=$(du -sk "$PKG/usr" | cut -f1)
cat > "$PKG/DEBIAN/control" <<CTL
Package: sottovoce
Version: $VERSION
Section: utils
Priority: optional
Architecture: all
Depends: python3 (>= 3.8), python3-gi, python3-gi-cairo, gir1.2-gtk-4.0, gir1.2-adw-1, python3-openpyxl, python3-cryptography
Installed-Size: $SIZE
Replaces: moodlog
Conflicts: moodlog
Provides: moodlog
Maintainer: Sottovoce <sottovoce@localhost>
Homepage: https://github.com/Akii-G/sottovoce
Description: A quiet mood diary
 Log how you feel in one click, with energy, anxiety, feeling words and
 notes; see charts, keep a dream journal, answer PHQ-9 and GAD-7 and make
 a PDF report for your doctor. Everything stays in plain files on your
 devices. Formerly called Moodlog.
CTL
dpkg-deb --root-owner-group -Zxz --build "$PKG" "sottovoce_${VERSION}_all.deb"
