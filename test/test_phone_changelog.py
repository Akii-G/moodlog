"""The phones' changelog.json files match src/moodlog and every line has Italian."""
import os, subprocess, sys
here = os.path.dirname(os.path.abspath(__file__))
run = subprocess.run([sys.executable, os.path.join(here, "..", "tools", "phone_changelog.py"), "--check"],
                     capture_output=True, text=True)
assert run.returncode == 0, run.stdout + run.stderr
print("PHONE CHANGELOG OK")
