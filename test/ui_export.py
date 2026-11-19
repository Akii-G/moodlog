import os; os.environ["MOODLOG_NO_WHATS_NEW"] = "1"
import importlib.machinery, importlib.util, os, csv, subprocess
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
from gi.repository import GLib
app = m.App("log"); err = []
def s1():
    try:
        w = app.get_active_window()
        assert w.export_name().startswith("moodlog-export-") and w.export_name().endswith(".csv")
        w.write_export("/tmp/ui_export")                      # .csv added automatically
        rows = list(csv.reader(open("/tmp/ui_export.csv", encoding="utf-8-sig")))
        assert rows[0][:4] == ["Date", "Weekday", "Mood", "Mood score (1–5)"] and "Sleep (hours)" in rows[0]
        assert w.saved_label.get_text().startswith("Exported ")
        w.write_export("/nonexistent-dir/x.csv"); assert w.saved_label.get_text().startswith("Could not export")
        w.write_export("/tmp/ui_export_x", "xlsx")
        import openpyxl
        wb = openpyxl.load_workbook("/tmp/ui_export_x.xlsx")
        assert wb.sheetnames == ["Summary", "Days", "Entries", "Events"], wb.sheetnames
        assert wb["Days"]["C1"].value == "Mood" and wb["Days"].freeze_panes == "C2"
        assert w.lookup_action("export-xlsx") and w.lookup_action("export-csv")
        w.activate_action("win.export-xlsx", None)            # menu item opens the save dialog
    except Exception:
        import traceback; traceback.print_exc(); err.append(1)
    return False
def s2():
    subprocess.run(["import", "-window", "root", "/tmp/export_dialog.png"]); app.quit(); return False
GLib.timeout_add(1500, s1); GLib.timeout_add(4000, s2)
app.run([]); print("ERRORS" if err else "EXPORT UI OK")
