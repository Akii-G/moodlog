# PyInstaller recipe for Sottovoce on a Mac (run from the repository root with Homebrew's
# Python, after copying src/moodlog to build/moodlog.py and making build/Sottovoce.icns).
# Makes dist/Sottovoce.app.
import os, re
from PyInstaller.utils.hooks import collect_submodules

root = os.path.abspath(os.path.join(SPECPATH, "..", ".."))
version = re.search(r'^VERSION = "(.*)"', open(os.path.join(root, "src", "moodlog")).read(), re.M).group(1)
datas = [(os.path.join(root, "po", "*.po"), "po"),
         (os.path.join(root, "data", "app.moodlog.Moodlog.svg"), "share/icons/hicolor/scalable/apps")]

a = Analysis(
    [os.path.join(root, "build", "moodlog.py")],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=["http.server", "plistlib", "cryptography.hazmat.primitives.ciphers.aead",
                   "cryptography.hazmat.primitives.kdf.hkdf"] + collect_submodules("openpyxl"),
    hookspath=[],
    hooksconfig={
        "gi": {
            "icons": ["Adwaita", "hicolor"],
            "themes": ["Adwaita"],
            "module-versions": {"Gtk": "4.0", "Gdk": "4.0", "Adw": "1", "GdkPixbuf": "2.0"},
        },
    },
    excludes=["tkinter", "unittest", "pydoc_data"],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(pyz, a.scripts, [], exclude_binaries=True, name="Sottovoce", console=False, debug=False,
          strip=False, upx=False, argv_emulation=False)
coll = COLLECT(exe, a.binaries, a.datas, strip=False, upx=False, name="Sottovoce")
app = BUNDLE(
    coll,
    name="Sottovoce.app",
    icon=os.path.join(root, "build", "Sottovoce.icns"),
    bundle_identifier="app.moodlog.Sottovoce",
    version=version,
    info_plist={
        "CFBundleDisplayName": "Sottovoce",
        "CFBundleShortVersionString": version,
        "CFBundleVersion": version,
        "NSHighResolutionCapable": True,
        "LSApplicationCategoryType": "public.app-category.healthcare-fitness",
        "LSMinimumSystemVersion": "13.0",
        "NSHumanReadableCopyright": "MIT License",
        "NSLocalNetworkUsageDescription": "Sottovoce receives your moods from Sottovoce on your phone over your home Wi-Fi.",
        "NSAppleEventsUsageDescription": "Sottovoce shows its daily reminder as a notification.",
        "CFBundleDevelopmentRegion": "en",
        "CFBundleLocalizations": ["en", "it"],
    },
)
