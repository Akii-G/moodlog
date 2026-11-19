"""The phone sync receiver switches to a new Moodlog by itself after an update,
in the same process, and ignores a file that is broken or still being written."""
import json, os, shutil, subprocess, sys, tempfile, time, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
tmp = tempfile.mkdtemp()
script = os.path.join(tmp, "moodlog")
shutil.copy(os.path.join(HERE, "..", "src", "moodlog"), script)
data = os.path.join(tmp, "data"); os.makedirs(data)
token, port = "receiver-test-token-0123", 18799
json.dump({"token": token, "port": port}, open(os.path.join(data, "sync.json"), "w"))
env = dict(os.environ, MOODLOG_DATA_DIR=data, MOODLOG_UPDATE_CHECK="0.5")
proc = subprocess.Popen([sys.executable, script, "--sync-server"], env=env,
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)


def version(wait=8.0):
    end = time.time() + wait
    while time.time() < end:
        try:
            req = urllib.request.Request("http://127.0.0.1:%d/api/ping" % port,
                                         headers={"Authorization": "Bearer " + token})
            return json.loads(urllib.request.urlopen(req, timeout=2).read())["version"]
        except OSError:
            time.sleep(0.3)
    return None


def replace(text):  # like dpkg: write a new file, then rename it over the old one
    with open(script + ".new", "w") as f:
        f.write(text)
    os.replace(script + ".new", script)


try:
    first = version()
    assert first, "the receiver did not start"
    src = open(script).read()
    replace("this is not python (")                       # broken: must keep running the old one
    time.sleep(2.5)
    assert version() == first and proc.poll() is None
    replace(src.replace('VERSION = "%s"' % first, 'VERSION = "9.9.9"'))
    end = time.time() + 10
    while time.time() < end and version(1) != "9.9.9":
        time.sleep(0.5)
    assert version() == "9.9.9", "did not switch to the new version"
    assert proc.poll() is None, "the process ended instead of restarting in place"
finally:
    proc.terminate()
    out = proc.communicate(timeout=5)[0]
assert out.count("restarting the sync receiver") == 1, out
print("RECEIVER UPDATE OK")
