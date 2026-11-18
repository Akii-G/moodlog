import importlib.machinery, importlib.util, datetime as dt, json, os, sys
loader = importlib.machinery.SourceFileLoader("moodlog", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "moodlog"))
spec = importlib.util.spec_from_loader("moodlog", loader); m = importlib.util.module_from_spec(spec); loader.exec_module(m)
D = dt.date
# numbers, English and Italian style
assert m.parse_number("8,234", whole=True) == 8234
assert m.parse_number("8.234", whole=True) == 8234
assert m.parse_number("8234.0", whole=True) == 8234
assert m.parse_number("12.345 count", whole=True) == 12345
assert m.parse_number("7,5") == 7.5 and m.parse_number("58.2 bpm") == 58.2
assert m.parse_number("1.234,5") == 1234.5 and m.parse_number("1,234.5") == 1234.5
assert m.parse_number("n/a") is None
# dates
assert m.parse_when("2026-11-12").date() == D(2026, 11, 12)
assert m.parse_when("2026-11-12T00:00:00+01:00") is not None
assert m.parse_when("12 Nov 2026 at 00:00") is None
# Shortcuts-style lines inside JSON {"data": "..."}
lines = "\n".join([
    "steps;2026-11-10T00:00:00+01:00;8.234",
    "steps;2026-11-11T00:00:00+01:00;10,512",
    "Resting Heart Rate;2026-11-11T00:00:00+01:00;58",
    "resting_hr;2026-11-10T00:00:00+01:00;61 bpm",
    "sleep;2026-11-10T23:30:00+01:00;2026-11-11T07:00:00+01:00;In Bed",
    "sleep;2026-11-10T23:40:00+01:00;2026-11-11T01:10:00+01:00;Core",
    "sleep;2026-11-11T01:10:00+01:00;2026-11-11T02:00:00+01:00;Deep",
    "sleep;2026-11-11T02:00:00+01:00;2026-11-11T02:10:00+01:00;Awake",
    "sleep;2026-11-11T02:10:00+01:00;2026-11-11T06:40:00+01:00;REM",
    "sleep;2026-11-11T02:10:00+01:00;2026-11-11T06:40:00+01:00;REM",   # duplicate source
    "sleep;2026-11-11T23:50:00+01:00;2026-11-12T06:50:00+01:00;Sonno principale",
    "steps;12 Nov 2026;900",
    "garbage",
])
os.environ["TZ"] = "Europe/Rome"; import time; time.tzset()
inc, problems = m.parse_payload(json.dumps({"data": lines}).encode())
assert inc["steps"] == {D(2026,11,10): 8234, D(2026,11,11): 10512}, inc["steps"]
assert inc["resting_hr"] == {D(2026,11,11): 58, D(2026,11,10): 61}
assert abs(inc["sleep_hours"][D(2026,11,11)] - (1.5 + 50/60 + 4.5)) < 0.01, inc["sleep_hours"]
assert inc["sleep_hours"][D(2026,11,12)] == 7.0
assert len(problems) == 2, problems
# plain text body, simple JSON record, list, Health Auto Export shape
inc, pr = m.parse_payload(b"steps;2026-11-12;4000\nsleep;2026-11-12;7,5\n")
assert inc == {"steps": {D(2026,11,12): 4000}, "sleep_hours": {D(2026,11,12): 7.5}} and not pr
inc, pr = m.parse_payload(b'{"date":"2026-11-12","steps":4000,"sleep_hours":7.2,"resting_hr":"59"}')
assert inc["sleep_hours"][D(2026,11,12)] == 7.2 and inc["resting_hr"][D(2026,11,12)] == 59
inc, pr = m.parse_payload(json.dumps({"data": {"metrics": [
    {"name": "step_count", "units": "count", "data": [{"date": "2026-11-12 00:00:00 +0100", "qty": 5000.4}]},
    {"name": "sleep_analysis", "data": [{"date": "2026-11-12 00:00:00 +0100", "totalSleep": 6.9, "inBed": 7.5}]},
    {"name": "heart_rate", "data": [{"date": "2026-11-12 00:00:00 +0100", "Min": 50, "Avg": 71, "Max": 130}]}]}}).encode())
assert inc["steps"][D(2026,11,12)] == 5000.4 and inc["sleep_hours"][D(2026,11,12)] == 6.9 and inc["heart_rate"][D(2026,11,12)] == 71
inc, pr = m.parse_payload(b"\xff\xfe nonsense"); assert not inc and pr
# merge rules
os.environ["MOODLOG_DATA_DIR"] = sys.argv[1]
hs = m.HealthStore(); hs.values = {}
hs.merge({"steps": {D(2026,11,10): 9000}, "resting_hr": {D(2026,11,10): 60, D(2026,11,11): 58}})
hs.merge({"steps": {D(2026,11,10): 3000, D(2026,11,11): 500}, "resting_hr": {D(2026,11,10): 70, D(2026,11,11): 57}})
assert hs.values["steps"] == {D(2026,11,10): 9000, D(2026,11,11): 500}
assert hs.values["resting_hr"] == {D(2026,11,10): 60, D(2026,11,11): 57}
hs.save(); assert m.HealthStore().values == hs.values
print("parser + merge OK")

# sleep sent with dates only (Shortcuts "Include time" off) must not become 24 h nights
inc, pr = m.parse_payload(b"sleep;2026-11-15;2026-11-16;Core\nsleep;2026-11-16;2026-11-17;REM\nsteps;2026-11-16;5000\n")
assert "sleep_hours" not in inc and inc["steps"] == {D(2026, 11, 16): 5000}, inc
assert len(pr) == 2 and pr[0].startswith("sleep times arrived without a clock time"), pr
# 24 h values already stored are dropped when the file is loaded, and the file is cleaned
open(os.path.join(sys.argv[1], "health.csv"), "w").write(
    "date,metric,value\n2026-11-15,sleep_hours,24\n2026-11-16,sleep_hours,7.25\n2026-11-16,steps,5000\n")
h = m.HealthStore()
assert h.values["sleep_hours"] == {D(2026, 11, 16): 7.25} and h.values["steps"] == {D(2026, 11, 16): 5000}
assert "24" not in open(os.path.join(sys.argv[1], "health.csv")).read().split("2026-11-16")[0]
print("date-only sleep OK")
