#!/bin/sh
# Builds moodlog_<version>_all.deb in the current folder.
set -e
cd "$(dirname "$0")"
VERSION=$(sed -n 's/^VERSION = "\(.*\)"/\1/p' src/moodlog)
PKG=build/moodlog_${VERSION}_all
rm -rf build && mkdir -p "$PKG/DEBIAN"
install -Dm755 src/moodlog "$PKG/usr/bin/moodlog"
install -Dm644 data/app.moodlog.Moodlog.desktop "$PKG/usr/share/applications/app.moodlog.Moodlog.desktop"
install -Dm644 data/app.moodlog.Moodlog.svg "$PKG/usr/share/icons/hicolor/scalable/apps/app.moodlog.Moodlog.svg"
install -Dm644 data/moodlog-sync.service "$PKG/usr/lib/systemd/user/moodlog-sync.service"
install -Dm644 data/moodlog-reminder.service "$PKG/usr/lib/systemd/user/moodlog-reminder.service"
for po in po/*.po; do install -Dm644 "$po" "$PKG/usr/share/moodlog/po/$(basename "$po")"; done
install -Dm644 LICENSE "$PKG/usr/share/doc/moodlog/copyright"
install -Dm644 README.md "$PKG/usr/share/doc/moodlog/README.md"
SIZE=$(du -sk "$PKG/usr" | cut -f1)
cat > "$PKG/DEBIAN/control" <<CTL
Package: moodlog
Version: $VERSION
Section: utils
Priority: optional
Architecture: all
Depends: python3 (>= 3.8), python3-gi, python3-gi-cairo, gir1.2-gtk-4.0, gir1.2-adw-1, python3-openpyxl, python3-cryptography
Installed-Size: $SIZE
Maintainer: Moodlog <moodlog@localhost>
Description: Small local mood tracker
 Log one mood a day with an optional note, browse them in a month
 calendar and see simple charts. Data is kept in a plain CSV file.
CTL
dpkg-deb --root-owner-group -Zxz --build "$PKG" "moodlog_${VERSION}_all.deb"
