"""Windows only (run by .github/workflows/windows.yml): the phone receiver starts and stops
without systemd, the reminder becomes a Task Scheduler task, and a notification can be shown."""
import os, sys, tempfile, time, subprocess, importlib.machinery, importlib.util
if sys.platform != "win32":
    print("windows: skipped (not Windows)")
    sys.exit(0)
data = tempfile.mkdtemp(); cfgdir = tempfile.mkdtemp()
os.environ.update(MOODLOG_DATA_DIR=data, MOODLOG_CONFIG_DIR=cfgdir, MOODLOG_LANG="en")
here = os.path.dirname(os.path.abspath(__file__))
script = os.path.join(here, "..", "src", "moodlog")
sys.argv = [script]
loader = importlib.machinery.SourceFileLoader("moodlog", script)
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)

assert m.IS_WINDOWS
assert m.self_command("--remind")[-1] == "--remind"
print("dirs:", m.default_data_dir(), m.default_config_dir())
assert m.default_data_dir() != m.default_config_dir()

ok, err = m.receiver_set(True)
assert ok, err
for _ in range(40):
    if m.receiver_running():
        break
    time.sleep(0.5)
assert m.receiver_running(), open(os.path.join(cfgdir, "moodlog.log")).read() if os.path.exists(os.path.join(cfgdir, "moodlog.log")) else "not running"
assert m.win_autostart_on() and os.path.exists(m.receiver_pid_path())
ok, err = m.receiver_set(False)
assert ok, err
for _ in range(20):
    if not m.receiver_running():
        break
    time.sleep(0.5)
assert not m.receiver_running() and not m.win_autostart_on()

ok, err = m.win_apply_reminder(True, "20:30")
assert ok, err
q = subprocess.run(["schtasks", "/Query", "/TN", m.WIN_TASK, "/V", "/FO", "LIST"], capture_output=True, text=True)
assert q.returncode == 0 and "--remind" in q.stdout, q.stdout + q.stderr
assert m.win_apply_reminder(False, "20:30")[0]
assert subprocess.run(["schtasks", "/Query", "/TN", m.WIN_TASK], capture_output=True).returncode != 0

print("toast:", m.win_toast("Moodlog test", "A test notification"))
print("windows ok")
