# PyInstaller recipe for Moodlog on Windows (run from the repository root, in MSYS2 UCRT64,
# after copying src/moodlog to build/moodlog.py). Makes dist/Moodlog/ with Moodlog.exe.
import os
from PyInstaller.utils.hooks import collect_submodules

root = os.path.abspath(os.path.join(SPECPATH, "..", ".."))
datas = [(os.path.join(root, "po", "*.po"), "po"),
         (os.path.join(root, "data", "app.moodlog.Moodlog.svg"), "share/icons/hicolor/scalable/apps")]

# GLib starts gdbus.exe for GApplication on Windows; without it GLib warns and may give up
import shutil
binaries = [(shutil.which("gdbus.exe") or shutil.which("gdbus"), ".")] if (shutil.which("gdbus.exe") or shutil.which("gdbus")) else []

a = Analysis(
    [os.path.join(root, "build", "moodlog.py")],
    pathex=[],
    binaries=binaries,
    datas=datas,
    hiddenimports=["http.server", "cryptography.hazmat.primitives.ciphers.aead",
                   "cryptography.hazmat.primitives.kdf.hkdf", "winreg"] + collect_submodules("openpyxl"),
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
exe = EXE(pyz, a.scripts, [], exclude_binaries=True, name="Moodlog", icon=os.path.join(SPECPATH, "moodlog.ico"),
          console=False, debug=False, strip=False, upx=False)
coll = COLLECT(exe, a.binaries, a.datas, strip=False, upx=False, name="Moodlog")
